package gregtech.api.recipes.logic;

import gregtech.api.recipes.conditions.ConditionDescription;
import gregtech.api.recipes.conditions.ConditionDescriptionCodec;
import gregtech.api.recipes.conditions.ConditionEvaluation;
import gregtech.api.recipes.conditions.ConditionState;
import gregtech.api.recipes.modifiers.RecipeModifierResult;
import gregtech.api.util.TextFormattingUtil;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** A bounded, immutable display snapshot. It is deliberately not persisted with machine NBT. */
public final class RecipeFailure {

    public static final RecipeFailure NONE = new RecipeFailure(RecipeFailureReason.NONE, 0, 0);
    private static final Map<RecipeFailureReason, RecipeFailure> SIMPLE = new EnumMap<>(RecipeFailureReason.class);

    static {
        for (RecipeFailureReason reason : RecipeFailureReason.values()) {
            SIMPLE.put(reason, reason == RecipeFailureReason.NONE ? NONE : new RecipeFailure(reason, 0, 0));
        }
    }

    private final RecipeFailureReason reason;
    private final long required;
    private final long available;
    private final List<ConditionDescription> conditions;

    private RecipeFailure(RecipeFailureReason reason, long required, long available) {
        this(reason, required, available, Collections.emptyList());
    }

    private RecipeFailure(RecipeFailureReason reason, long required, long available,
                          List<ConditionDescription> conditions) {
        this.reason = Objects.requireNonNull(reason);
        this.required = required;
        this.available = available;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
    }

    public static RecipeFailure of(RecipeFailureReason reason) {
        return SIMPLE.get(reason);
    }

    public static RecipeFailure of(RecipeFailureReason reason, long required, long available) {
        return reason.hasAmounts() ? new RecipeFailure(reason, required, available) : of(reason);
    }

    public static RecipeFailure condition(ConditionEvaluation result) {
        if (result.isSuccess()) return NONE;
        return new RecipeFailure(result.state() == ConditionState.UNAVAILABLE ?
                RecipeFailureReason.CONDITION_UNAVAILABLE : RecipeFailureReason.CONDITION, 0, 0, result.alternatives());
    }

    public static RecipeFailure modifier(RecipeModifierResult result) {
        if (result.isSuccess()) return NONE;
        RecipeFailureReason reason = switch (result.status()) {
            case CANCELLED -> RecipeFailureReason.MODIFIER_CANCELLED;
            case UNSUPPORTED -> RecipeFailureReason.MODIFIER_UNSUPPORTED;
            default -> RecipeFailureReason.MODIFIER_INVALID;
        };
        return new RecipeFailure(reason, 0, 0, Collections.singletonList(new ConditionDescription(
                "gregtech.recipe.modifier.detail", result.modifierId(), Integer.toString(result.index() + 1),
                result.reasonKey())));
    }

    public List<ConditionDescription> getConditionDescriptions() {
        return conditions;
    }

    public RecipeFailureReason getReason() {
        return reason;
    }

    public long getRequired() {
        return required;
    }

    public long getAvailable() {
        return available;
    }

    public boolean isEmpty() {
        return reason == RecipeFailureReason.NONE;
    }

    public Object[] getTranslationArguments() {
        return reason.hasAmounts() ? new Object[] { TextFormattingUtil.formatNumbers(required),
                TextFormattingUtil.formatNumbers(available) } : new Object[0];
    }

    public String format(BiFunction<String, Object[], String> translator) {
        String text = translator.apply(reason.getTranslationKey(), getTranslationArguments());
        if (conditions.isEmpty()) return text;
        if (reason.hasModifierDetails()) {
            ConditionDescription detail = conditions.get(0);
            if (!detail.key().equals("gregtech.recipe.modifier.detail") || detail.arguments().size() != 3) return text;
            List<String> arguments = detail.arguments();
            String description = arguments.get(0) + " [" + arguments.get(1) + "]: " +
                    translator.apply(arguments.get(2), new Object[0]);
            if (description.length() > 256) description = description.substring(0, 253) + "...";
            return text + ": " + description;
        }
        List<String> alternatives = new ArrayList<>();
        for (ConditionDescription description : conditions) alternatives.add(description.format(translator));
        String details = String.join(" " + translator.apply("gregtech.recipe.condition.or", new Object[0]) + " ",
                alternatives);
        if (details.length() > 256) details = details.substring(0, 253) + "...";
        return text + ": " + details;
    }

    /** Existing reasons retain their fixed payload; condition/modifier details share a bounded display-only codec. */
    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(reason.getId());
        buffer.writeLong(required);
        buffer.writeLong(available);
        if (reason.hasConditions()) ConditionDescriptionCodec.write(buffer, conditions);
    }

    public static RecipeFailure read(PacketBuffer buffer) {
        RecipeFailureReason reason = RecipeFailureReason.fromId(buffer.readVarInt());
        long required = buffer.readLong();
        long available = buffer.readLong();
        return reason.hasConditions() ? new RecipeFailure(reason, 0, 0, ConditionDescriptionCodec.read(buffer)) :
                of(reason, required, available);
    }

    /** Probe transport, independent of the machine's persisted processing snapshot. */
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Failure", reason.getId());
        tag.setLong("Required", required);
        tag.setLong("Available", available);
        if (reason.hasConditions()) tag.setTag("ConditionDetails", ConditionDescriptionCodec.serialize(conditions));
    }

    public static RecipeFailure readFromNBT(NBTTagCompound tag) {
        RecipeFailureReason reason = RecipeFailureReason.fromId(tag.getInteger("Failure"));
        return reason.hasConditions() ?
                new RecipeFailure(reason, 0, 0, ConditionDescriptionCodec.deserialize(tag.getTag("ConditionDetails"))) :
                of(reason, tag.getLong("Required"), tag.getLong("Available"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeFailure)) return false;
        RecipeFailure that = (RecipeFailure) other;
        return reason == that.reason && required == that.required && available == that.available &&
                conditions.equals(that.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, required, available, conditions);
    }
}

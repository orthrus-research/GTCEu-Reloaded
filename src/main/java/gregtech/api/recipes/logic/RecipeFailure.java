package gregtech.api.recipes.logic;

import gregtech.api.util.TextFormattingUtil;

import net.minecraft.network.PacketBuffer;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

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

    private RecipeFailure(RecipeFailureReason reason, long required, long available) {
        this.reason = Objects.requireNonNull(reason);
        this.required = required;
        this.available = available;
    }

    public static RecipeFailure of(RecipeFailureReason reason) {
        return SIMPLE.get(reason);
    }

    public static RecipeFailure of(RecipeFailureReason reason, long required, long available) {
        return reason.hasAmounts() ? new RecipeFailure(reason, required, available) : of(reason);
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

    /** Widget transport only; always the same bounded payload, including unknown future identifiers. */
    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(reason.getId());
        buffer.writeLong(required);
        buffer.writeLong(available);
    }

    public static RecipeFailure read(PacketBuffer buffer) {
        return of(RecipeFailureReason.fromId(buffer.readVarInt()), buffer.readLong(), buffer.readLong());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeFailure)) return false;
        RecipeFailure that = (RecipeFailure) other;
        return reason == that.reason && required == that.required && available == that.available;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, required, available);
    }
}

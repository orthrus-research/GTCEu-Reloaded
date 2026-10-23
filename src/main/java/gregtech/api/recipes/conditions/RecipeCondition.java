package gregtech.api.recipes.conditions;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Immutable recipe-local descriptor with a stable codec identity. */
public final class RecipeCondition<T> {

    private final RecipeConditionType<T> type;
    private final T value;
    private final boolean inverted;
    private final NBTTagCompound serialized;
    private final ConditionDescription description;

    RecipeCondition(RecipeConditionType<T> type, T value, NBTTagCompound data, boolean inverted,
                    ConditionDescription description) {
        this.type = type;
        this.value = value;
        this.inverted = inverted;
        this.description = description;
        this.serialized = new NBTTagCompound();
        serialized.setString("Type", type.id().toString());
        serialized.setInteger("Version", 1);
        serialized.setBoolean("Inverted", inverted);
        serialized.setTag("Data", data.copy());
        if (!ConditionPayloads.bounded(serialized))
            throw new IllegalArgumentException("Condition descriptor exceeds its bounds");
    }

    private RecipeCondition(NBTTagCompound unavailable) {
        this.type = null;
        this.value = null;
        this.inverted = false;
        this.serialized = unavailable.copy();
        String id = unavailable.getString("Type");
        this.description = new ConditionDescription("gregtech.recipe.condition.unavailable",
                id.length() <= 128 ? id : "invalid")
                        .withType(id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? id :
                                "gregtech:invalid_condition");
    }

    public ConditionState test(RecipeConditionContext context) {
        if (type == null || context == null) return ConditionState.UNAVAILABLE;
        try {
            if (!context.isAvailable()) return ConditionState.UNAVAILABLE;
            return type.test(value, context).inverted(inverted);
        } catch (RuntimeException exception) {
            return ConditionState.UNAVAILABLE;
        }
    }

    public ConditionDescription description() {
        return description;
    }

    public boolean isAlternative() {
        return type != null && type.isAlternative();
    }

    public String typeId() {
        return serialized.getString("Type");
    }

    public NBTTagCompound serialize() {
        return serialized.copy();
    }

    public static RecipeCondition<?> deserialize(NBTBase input) {
        if (!(input instanceof NBTTagCompound) || !ConditionPayloads.bounded(input)) return unavailable();
        NBTTagCompound tag = (NBTTagCompound) input;
        try {
            ConditionPayloads.requireKeys(tag, "Type", "Version", "Inverted", "Data");
            ConditionPayloads.requireTag(tag, "Type", 8);
            ConditionPayloads.requireTag(tag, "Version", 3);
            ConditionPayloads.requireTag(tag, "Inverted", 1);
            ConditionPayloads.requireTag(tag, "Data", 10);
            if (tag.getInteger("Version") != 1 || (tag.getByte("Inverted") != 0 && tag.getByte("Inverted") != 1) ||
                    !tag.getString("Type").matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                return new RecipeCondition<>(tag);
            }
            RecipeConditionType<?> type = RecipeConditions.type(new ResourceLocation(tag.getString("Type")));
            return type == null ? new RecipeCondition<>(tag) :
                    type.read(tag.getCompoundTag("Data"), tag.getBoolean("Inverted"));
        } catch (RuntimeException exception) {
            return new RecipeCondition<>(tag);
        }
    }

    static RecipeCondition<?> unavailable() {
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setString("Type", "gregtech:invalid_condition");
        invalid.setInteger("Version", -1);
        return new RecipeCondition<>(invalid);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecipeCondition && serialized.equals(((RecipeCondition<?>) other).serialized);
    }

    @Override
    public int hashCode() {
        return serialized.hashCode();
    }
}

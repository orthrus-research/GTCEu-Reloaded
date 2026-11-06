package gregtech.api.recipes.definition;

import gregtech.api.GTValues;

import net.minecraft.item.ItemStack;

import java.util.Objects;

/** Immutable vanilla-furnace effect. Input matching uses item and metadata, never count, NBT or capabilities. */
public record FurnaceRecipeDefinition(ItemValue input, ItemValue output, float experience) {

    public FurnaceRecipeDefinition {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        ItemStack stack = input.stack();
        if (stack.isEmpty() || stack.getCount() != 1 || stack.hasTagCompound() ||
                stack.serializeNBT().hasKey("ForgeCaps")) {
            throw new IllegalArgumentException("Furnace input needs one item without NBT or capability constraints");
        }
        ItemStack result = output.stack();
        if (result.isEmpty() || result.getMetadata() == GTValues.W || result.getCount() > result.getMaxStackSize()) {
            throw new IllegalArgumentException(
                    "Furnace output needs a concrete, nonempty stack within its stack limit");
        }
        if (!Float.isFinite(experience) || experience < 0) {
            throw new IllegalArgumentException("Furnace experience must be finite and nonnegative");
        }
        if (experience == 0) experience = 0; // Canonicalize negative zero.
    }

    /** Explicitly selects vanilla's one-item input semantics; the caller's stacks are never mutated. */
    public static FurnaceRecipeDefinition of(ItemStack input, ItemStack output, float experience) {
        ItemStack single = Objects.requireNonNull(input, "input").copy();
        if (!single.isEmpty()) single.setCount(1);
        return new FurnaceRecipeDefinition(ItemValue.of(single), ItemValue.of(output), experience);
    }
}

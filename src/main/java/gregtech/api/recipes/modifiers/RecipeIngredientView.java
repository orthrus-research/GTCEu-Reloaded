package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.ingredients.GTRecipeInput;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.Nullable;

/** Ingredient identity summaries; mutable matcher and ingredient objects are intentionally not exposed. */
public final class RecipeIngredientView {

    private final int amount;
    private final boolean nonConsumable;
    private final boolean nbtMatching;
    private final String oreName;
    private final ItemStack[] items;
    private final FluidStack fluid;

    RecipeIngredientView(GTRecipeInput input) {
        this.amount = input.getAmount();
        this.nonConsumable = input.isNonConsumable();
        this.nbtMatching = input.hasNBTMatchingCondition();
        this.oreName = input.isOreDict() ? OreDictionary.getOreName(input.getOreDict()) : null;
        ItemStack[] inputStacks = input.getInputStacks();
        this.items = inputStacks == null ? new ItemStack[0] : copy(inputStacks);
        FluidStack inputFluid = input.getInputFluidStack();
        this.fluid = inputFluid == null ? null : inputFluid.copy();
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        ItemStack[] result = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) result[i] = stacks[i].copy();
        return result;
    }

    public int amount() {
        return amount;
    }

    public boolean isNonConsumable() {
        return nonConsumable;
    }

    public boolean hasNbtMatching() {
        return nbtMatching;
    }

    public @Nullable String oreName() {
        return oreName;
    }

    public ItemStack[] items() {
        return copy(items);
    }

    public @Nullable FluidStack fluid() {
        return fluid == null ? null : fluid.copy();
    }
}

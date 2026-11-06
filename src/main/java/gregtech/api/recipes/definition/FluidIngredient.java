package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;

import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

/** Native fluid ingredient with exact tag equality, including the distinction between absent and empty tags. */
public record FluidIngredient(FluidValue value, boolean consumable) {

    public FluidIngredient {
        Objects.requireNonNull(value, "value");
    }

    public static FluidIngredient exact(FluidStack stack) {
        return new FluidIngredient(FluidValue.of(stack), true);
    }

    public GTRecipeInput compile() {
        if (value.amount() <= 0) throw new IllegalArgumentException("Fluid amount must be positive");
        GTRecipeInput input = new GTRecipeFluidInput(value.stack());
        return consumable ? input : input.setNonConsumable();
    }
}

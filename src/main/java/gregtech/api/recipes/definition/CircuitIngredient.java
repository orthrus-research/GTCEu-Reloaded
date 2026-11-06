package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;

/** A native integrated circuit selector; always nonconsumable and of quantity one. */
public record CircuitIngredient(int configuration) implements ItemInput {

    @Override
    public GTRecipeInput compile() {
        return IntCircuitIngredient.circuitInput(configuration);
    }
}

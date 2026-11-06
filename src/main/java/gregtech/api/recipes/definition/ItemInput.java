package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.GTRecipeInput;

/** Built-in immutable item input descriptions. Existing native ingredient extensions remain independent. */
public sealed interface ItemInput permits ItemIngredient, OreIngredient, MaterialIngredient, CircuitIngredient {

    GTRecipeInput compile();
}

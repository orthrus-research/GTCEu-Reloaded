package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.FurnaceRecipeDefinition;

import java.util.List;

record OwnedRecipeDefinition(RecipeMap<?> map, Recipe recipe, SourceOrigin origin, boolean recycling,
                             List<FurnaceRecipeDefinition> furnace) {

    OwnedRecipeDefinition {
        furnace = List.copyOf(furnace);
    }
}

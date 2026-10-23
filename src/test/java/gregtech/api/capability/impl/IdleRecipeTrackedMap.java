package gregtech.api.capability.impl;

import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ui.RecipeMapUI;

class IdleRecipeTrackedMap extends RecipeMap<SimpleRecipeBuilder> {

    private static int nextId;

    IdleRecipeTrackedMap() {
        super("idle_tracked_map_" + nextId++, new SimpleRecipeBuilder(),
                map -> new RecipeMapUI<>(map, true, true, true, true, false), 2, 2, 1, 1);
    }

    @Override
    public boolean supportsRecipeRevisionTracking() {
        return true;
    }

    void clearRecipes() {
        removeAllRecipes();
    }
}

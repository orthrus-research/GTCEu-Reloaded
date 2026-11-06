package gregtech.api.recipes;

import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.map.Branch;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal state prepared before publication; never exposed to script or addon callers. */
record PreparedRecipeMapChange(RecipeMap<?> map, long revision, Branch lookup,
                               Map<GTRecipeCategory, List<Recipe>> categories,
                               Set<Recipe> prefixRecipes,
                               boolean oreInputs, boolean materialInputs, boolean nbtInputs,
                               List<PreparedRecipeRegistrationState<?>> states) {}

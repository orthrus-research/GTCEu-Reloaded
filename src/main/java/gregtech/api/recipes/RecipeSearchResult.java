package gregtech.api.recipes;

import org.jetbrains.annotations.Nullable;

/** Distinguishes missing inputs from matching recipes whose environment is temporarily unsuitable. */
public final class RecipeSearchResult {

    private final Recipe recipe;
    private final Recipe firstMatch;

    public RecipeSearchResult(@Nullable Recipe recipe, @Nullable Recipe firstMatch) {
        this.recipe = recipe;
        this.firstMatch = firstMatch;
    }

    @Nullable
    public Recipe getRecipe() {
        return recipe;
    }

    public boolean hasInputMatch() {
        return firstMatch != null;
    }

    /** Retain an input match when all candidates are blocked so input notifications are not required to retry. */
    @Nullable
    public Recipe getRecipeForCache() {
        return recipe == null ? firstMatch : recipe;
    }
}

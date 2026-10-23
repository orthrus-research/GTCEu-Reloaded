package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.Recipe;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** A short-lived lookup-to-start cache. Handler availability remains a live check outside this source-only cache. */
public final class RecipeModifierAttempt {

    private final RecipeModifierList modifiers;
    private final RecipeModifierContext context;
    private final int itemOutputLimit;
    private final int fluidOutputLimit;
    private final Map<Recipe, RecipeModifierResult> results = new IdentityHashMap<>();
    private final Map<Recipe, RecipeModifierResult> adjusted = new IdentityHashMap<>();
    private boolean evaluating;
    private boolean reentered;

    public RecipeModifierAttempt(RecipeModifierList modifiers, RecipeModifierContext context, int itemOutputLimit,
                                 int fluidOutputLimit) {
        this.modifiers = Objects.requireNonNull(modifiers);
        this.context = Objects.requireNonNull(context);
        this.itemOutputLimit = itemOutputLimit;
        this.fluidOutputLimit = fluidOutputLimit;
    }

    public RecipeModifierResult adjust(Recipe source) {
        if (evaluating) {
            this.reentered = true;
            return unsupported();
        }
        RecipeModifierResult result = results.get(source);
        if (result == null) result = adjusted.get(source);
        if (result != null) return result;
        this.evaluating = true;
        this.reentered = false;
        try {
            try {
                Recipe trimmed = RecipeModifierCopies.trim(source, itemOutputLimit, fluidOutputLimit);
                result = modifiers.apply(context, trimmed);
            } catch (UnsupportedOperationException unsupported) {
                result = RecipeModifierResult.failure(RecipeModifierResult.Status.UNSUPPORTED, "gregtech:recipe_copy",
                        -1,
                        "gregtech.recipe.modifier.unsupported_source");
            } catch (RuntimeException invalid) {
                result = RecipeModifierResult.failure(RecipeModifierResult.Status.INVALID, "gregtech:recipe_copy", -1,
                        "gregtech.recipe.modifier.invalid_source");
            }
            if (reentered) result = unsupported();
            results.put(source, result);
            if (result.isSuccess()) adjusted.put(result.recipe(), result);
            return result;
        } finally {
            this.evaluating = false;
        }
    }

    public boolean isEvaluating() {
        return evaluating;
    }

    public RecipeModifierResult rejectReentry() {
        this.reentered = true;
        return unsupported();
    }

    private static RecipeModifierResult unsupported() {
        return RecipeModifierResult.failure(RecipeModifierResult.Status.UNSUPPORTED, "gregtech:startup", -1,
                "gregtech.recipe.modifier.reentrant");
    }
}

package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.IdentifierPatterns;

import java.util.Objects;
import java.util.function.BiFunction;

/** A named, pure callback. Captured addon state must also be read-only during evaluation. */
public final class RecipeModifier {

    private final String id;
    private final BiFunction<RecipeModifierContext, RecipeModifierView, RecipeModification> callback;

    private RecipeModifier(String id,
                           BiFunction<RecipeModifierContext, RecipeModifierView, RecipeModification> callback) {
        if (id == null || id.length() > 128 || !IdentifierPatterns.isResourceId(id))
            throw new IllegalArgumentException("Invalid recipe modifier identity");
        this.id = id;
        this.callback = Objects.requireNonNull(callback);
    }

    public static RecipeModifier named(String id,
                                       BiFunction<RecipeModifierContext, RecipeModifierView, RecipeModification> callback) {
        return new RecipeModifier(id, callback);
    }

    public String id() {
        return id;
    }

    RecipeModification evaluate(RecipeModifierContext context, RecipeModifierView recipe) {
        return Objects.requireNonNull(callback.apply(context, recipe), "A modifier must return a descriptor");
    }
}

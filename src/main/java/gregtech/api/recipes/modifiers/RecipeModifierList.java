package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.Recipe;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ordered pure startup pipeline. Each callback observes the previous descriptor's adjusted recipe. */
public final class RecipeModifierList {

    public static final int MAX_MODIFIERS = 32;
    public static final RecipeModifierList EMPTY = new RecipeModifierList(new RecipeModifier[0]);
    private final List<RecipeModifier> modifiers;

    private RecipeModifierList(RecipeModifier[] modifiers) {
        if (modifiers.length > MAX_MODIFIERS) throw new IllegalArgumentException("Too many recipe modifiers");
        RecipeModifier[] copy = modifiers.clone();
        Set<String> ids = new HashSet<>();
        for (RecipeModifier modifier : copy) {
            if (!ids.add(Objects.requireNonNull(modifier).id()))
                throw new IllegalArgumentException("Duplicate recipe modifier identity");
        }
        this.modifiers = Collections.unmodifiableList(Arrays.asList(copy));
    }

    public static RecipeModifierList of(RecipeModifier... modifiers) {
        return modifiers.length == 0 ? EMPTY : new RecipeModifierList(modifiers);
    }

    public boolean isEmpty() {
        return modifiers.isEmpty();
    }

    public List<RecipeModifier> modifiers() {
        return modifiers;
    }

    public RecipeModifierResult apply(RecipeModifierContext context, Recipe source) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(source);
        if (isEmpty()) return RecipeModifierResult.success(source);
        Recipe current;
        try {
            current = RecipeModifierCopies.copy(source);
        } catch (UnsupportedOperationException unsupported) {
            return RecipeModifierResult.failure(RecipeModifierResult.Status.UNSUPPORTED, "gregtech:recipe_copy", -1,
                    "gregtech.recipe.modifier.unsupported_source");
        } catch (RuntimeException invalid) {
            return RecipeModifierResult.failure(RecipeModifierResult.Status.INVALID, "gregtech:recipe_copy", -1,
                    "gregtech.recipe.modifier.invalid_source");
        }
        for (int i = 0; i < modifiers.size(); i++) {
            RecipeModifier modifier = modifiers.get(i);
            try {
                RecipeModification modification = modifier.evaluate(context, new RecipeModifierView(current));
                if (modification.cancellationKey() != null)
                    return RecipeModifierResult.failure(RecipeModifierResult.Status.CANCELLED, modifier.id(), i,
                            modification.cancellationKey());
                if (!modification.isIdentity()) current = RecipeModifierCopies.apply(current, modification);
            } catch (UnsupportedOperationException unsupported) {
                return RecipeModifierResult.failure(RecipeModifierResult.Status.UNSUPPORTED, modifier.id(), i,
                        "gregtech.recipe.modifier.unsupported_source");
            } catch (RuntimeException invalid) {
                // Do not include callback exception text in diagnostics, and never swallow fatal JVM Errors.
                return RecipeModifierResult.failure(RecipeModifierResult.Status.INVALID, modifier.id(), i,
                        "gregtech.recipe.modifier.invalid_result");
            }
        }
        return RecipeModifierResult.success(current);
    }
}

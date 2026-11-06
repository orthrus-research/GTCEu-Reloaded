package gregtech.api.recipes;

import java.util.Objects;

/** The exact primary recipe accepted by a native map; derived registrations have their own results. */
public record NativeRecipeRegistration(RecipeMap<?> map, Recipe recipe) {

    public NativeRecipeRegistration {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(recipe, "recipe");
    }
}

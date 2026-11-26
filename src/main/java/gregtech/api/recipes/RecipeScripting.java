package gregtech.api.recipes;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Connects recipe registration to the script frontend that authors recipes while a pack loads, which is GregTech's
 * GroovyScript integration. The recipe API calls the installed {@link Frontend} without depending on any scripting
 * mod: until one is installed, no script is running and every notice is dropped.
 */
public final class RecipeScripting {

    private static volatile Frontend frontend = new Frontend() {};

    private RecipeScripting() {}

    /**
     * @return whether a script is running now; recipes built meanwhile are script recipes
     *         ({@link Recipe#isGroovyRecipe()})
     */
    public static boolean isRunning() {
        return frontend.isRunning();
    }

    /** Installs the frontend of the scripting integration; only that integration calls this. */
    @ApiStatus.Internal
    public static void install(@NotNull Frontend frontend) {
        RecipeScripting.frontend = Objects.requireNonNull(frontend, "frontend");
    }

    static @NotNull Frontend frontend() {
        return frontend;
    }

    /** What a script frontend learns about recipe registration. Every method has a default that does nothing. */
    @ApiStatus.Internal
    public interface Frontend {

        /** @return whether a script is running now */
        default boolean isRunning() {
            return false;
        }

        /**
         * Reports the problems that stop a recipe a script declared.
         *
         * @param title    names the recipe map the script added to
         * @param problems one line for each problem
         * @return whether any problem was reported, so that the recipe is skipped
         */
        default boolean reportInvalid(@NotNull String title, @NotNull List<String> problems) {
            return false;
        }

        /**
         * Reports a script recipe that the map refused because another recipe already uses its inputs.
         *
         * @param details names the recipe or recipes it conflicts with
         */
        default void reportConflict(@NotNull RecipeMap<?> map, @NotNull Recipe recipe, @NotNull String details) {}

        /**
         * Called once for each recipe map as it is created.
         *
         * @param builderType the class of the map's recipe builders
         */
        default void mapCreated(@NotNull RecipeMap<?> map, @NotNull Class<?> builderType) {}

        /** A script recipe was added to the map. */
        default void scriptRecipeAdded(@NotNull RecipeMap<?> map, @NotNull Recipe recipe) {}

        /** A recipe left the map while a script ran; the frontend restores it before the scripts run again. */
        default void recipeRemoved(@NotNull RecipeMap<?> map, @NotNull Recipe recipe) {}
    }
}

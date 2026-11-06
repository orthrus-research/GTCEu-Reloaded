package gregtech.api.recipes;

import gregtech.api.recipes.definition.FurnaceRecipeDefinition;

import java.util.List;
import java.util.function.Consumer;

/**
 * A registration-time expansion shared by native builders and typed declarations. Implementations may mutate only
 * the supplied, unpublished builder and emit fresh derived builders. They must not write registries, retain builders
 * or perform other external effects. Arbitrary {@link RecipeBuildAction} callbacks do not have this contract.
 */
@FunctionalInterface
public interface RecipeExpansion<R extends RecipeBuilder<R>> extends RecipeBuildAction<R> {

    void expand(R builder, Consumer<RecipeBuilder<?>> derived);

    /** Pure declarations collected with the machine recipes and published by their registration owner. */
    default List<FurnaceRecipeDefinition> furnaceRecipes(R builder) {
        return List.of();
    }

    @Override
    default void accept(R builder) {
        expand(builder, RecipeBuilder::buildAndRegister);
        var furnace = List.copyOf(furnaceRecipes(builder));
        for (var recipe : furnace) {
            ModHandler.addSmeltingRecipe(recipe.input().stack(), recipe.output().stack(), recipe.experience());
        }
    }
}

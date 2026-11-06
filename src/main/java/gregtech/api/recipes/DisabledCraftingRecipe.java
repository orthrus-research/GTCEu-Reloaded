package gregtech.api.recipes;

import gregtech.api.recipes.recipes.DummyRecipe;

/** Keeps a removed recipe's numeric identity reserved without matching or entering the recipe book. */
final class DisabledCraftingRecipe extends DummyRecipe {

    @Override
    public boolean isDynamic() {
        return true;
    }
}

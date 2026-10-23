package gregtech.api.capability.impl;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

class PendingOutputTestLogic extends RecipeFailureTestLogic {

    int progressCalls;
    int conditionCalls;
    int completions;
    int setups;
    boolean forbidProcessing;

    PendingOutputTestLogic(PendingOutputTestMachine machine, RecipeMap<?> map) {
        super(machine, map);
    }

    @Override
    protected boolean shouldSearchForRecipes() {
        return false;
    }

    @Override
    protected void setupRecipe(Recipe recipe) {
        setups++;
        super.setupRecipe(recipe);
    }

    @Override
    protected void updateRecipeProgress() {
        if (forbidProcessing) throw new AssertionError("Completed recipe resumed resource-consuming progress");
        progressCalls++;
        super.updateRecipeProgress();
    }

    @Override
    protected boolean canProgressRecipe() {
        if (forbidProcessing) throw new AssertionError("Completed recipe rechecked processing conditions");
        conditionCalls++;
        return super.canProgressRecipe();
    }

    @Override
    protected void completeRecipe() {
        completions++;
        super.completeRecipe();
    }
}

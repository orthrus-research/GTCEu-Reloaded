package gregtech.api.capability.impl;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

class IdleRecipeTestLogic extends RecipeLogicEnergy {

    boolean scheduling = true;
    int callbacks;
    int searches;
    int conditions;
    int finalChecks;
    Runnable afterUpdate;
    Runnable beforeProgress;
    RecipeMap<?> selectedMap;
    boolean useSelectedMap;

    IdleRecipeTestLogic(IdleRecipeTestMachine machine, RecipeMap<?> map) {
        super(machine, map, machine::energy);
        this.selectedMap = map;
        this.useSelectedMap = true;
    }

    @Override
    protected boolean supportsIdleRecipeScheduling() {
        return scheduling;
    }

    @Override
    protected boolean supportsRecipeCandidateEligibility() {
        return true;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return useSelectedMap ? selectedMap : super.getRecipeMap();
    }

    @Override
    public void update() {
        callbacks++;
        super.update();
        if (afterUpdate != null) afterUpdate.run();
    }

    @Override
    protected void trySearchNewRecipe() {
        searches++;
        super.trySearchNewRecipe();
    }

    @Override
    protected void updateRecipeProgress() {
        if (beforeProgress != null) beforeProgress.run();
        super.updateRecipeProgress();
    }

    @Override
    protected boolean canProgressRecipe() {
        conditions++;
        return super.canProgressRecipe();
    }

    @Override
    public boolean checkRecipe(Recipe recipe) {
        finalChecks++;
        return super.checkRecipe(recipe);
    }
}

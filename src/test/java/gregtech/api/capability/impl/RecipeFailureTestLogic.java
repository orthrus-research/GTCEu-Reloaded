package gregtech.api.capability.impl;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

class RecipeFailureTestLogic extends AbstractRecipeLogic {

    long stored = 256;
    boolean drawAllowed = true;
    boolean finalAllowed = true;
    boolean preparationAllowed = true;
    int finalChecks;
    int preparations;
    int simulatedDraws;
    int actualDraws;

    RecipeFailureTestLogic(RecipeFailureTestMachine machine, RecipeMap<?> map) {
        super(machine, map);
    }

    @Override
    protected boolean supportsRecipeCandidateEligibility() {
        return true;
    }

    @Override
    public boolean checkRecipe(Recipe recipe) {
        finalChecks++;
        return finalAllowed && super.checkRecipe(recipe);
    }

    @Override
    public boolean prepareRecipe(Recipe recipe) {
        preparations++;
        return preparationAllowed && super.prepareRecipe(recipe);
    }

    @Override
    protected long getEnergyStored() {
        return stored;
    }

    @Override
    protected long getEnergyCapacity() {
        return 1000;
    }

    @Override
    protected long getEnergyInputPerSecond() {
        return 0;
    }

    @Override
    protected boolean drawEnergy(long eut, boolean simulate) {
        if (simulate) simulatedDraws++;
        else actualDraws++;
        return drawAllowed;
    }

    @Override
    public long getMaxVoltage() {
        return 32;
    }
}

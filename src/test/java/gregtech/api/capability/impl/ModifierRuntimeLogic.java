package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.ParallelLogicType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

final class ModifierRuntimeLogic extends RecipeFailureTestLogic {

    Recipe prepared;
    int setups;
    int completions;
    long voltage = 32;
    ParallelLogicType parallelType = ParallelLogicType.MULTIPLY;

    ModifierRuntimeLogic(ModifierRuntimeMachine machine, RecipeMap<?> map) {
        super(machine, map);
        setAllowOverclocking(false);
        this.stored = 2048;
    }

    @Override
    public long getMaxVoltage() {
        return voltage;
    }

    @Override
    public ParallelLogicType getParallelLogicType() {
        return parallelType;
    }

    @Override
    protected boolean shouldSearchForRecipes() {
        return false;
    }

    @Override
    protected void setupRecipe(Recipe recipe) {
        this.prepared = recipe;
        this.setups++;
        super.setupRecipe(recipe);
    }

    @Override
    protected boolean drawEnergy(long eut, boolean simulate) {
        boolean result = super.drawEnergy(eut, simulate);
        if (result && !simulate) this.stored -= eut;
        return result;
    }

    @Override
    protected void completeRecipe() {
        this.completions++;
        super.completeRecipe();
    }
}

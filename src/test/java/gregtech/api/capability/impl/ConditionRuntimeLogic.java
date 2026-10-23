package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.conditions.RecipeCondition;

import net.minecraftforge.items.IItemHandlerModifiable;

final class ConditionRuntimeLogic extends RecipeFailureTestLogic {

    boolean allowSearch;
    boolean legacyProgressAllowed = true;
    boolean candidateOptIn = true;
    RecipeCondition<?> extraPreparedCondition;
    int searches;
    int progressCalls;
    int legacyProgressChecks;
    int setups;
    int completions;

    ConditionRuntimeLogic(ConditionRuntimeMachine machine, RecipeMap<?> map) {
        super(machine, map);
    }

    @Override
    protected boolean supportsIdleRecipeScheduling() {
        return true;
    }

    @Override
    protected boolean supportsRecipeCandidateEligibility() {
        return candidateOptIn;
    }

    @Override
    protected boolean shouldSearchForRecipes() {
        return allowSearch && super.shouldSearchForRecipes();
    }

    @Override
    protected boolean canProgressRecipe() {
        legacyProgressChecks++;
        return legacyProgressAllowed && super.canProgressRecipe();
    }

    @Override
    protected Recipe findRecipe(long voltage, IItemHandlerModifiable items, IMultipleTankHandler fluids) {
        searches++;
        return super.findRecipe(voltage, items, fluids);
    }

    @Override
    public void applyParallelBonus(RecipeBuilder<?> builder) {
        if (extraPreparedCondition != null) builder.condition(extraPreparedCondition);
    }

    @Override
    protected void updateRecipeProgress() {
        progressCalls++;
        super.updateRecipeProgress();
    }

    @Override
    protected void setupRecipe(Recipe recipe) {
        setups++;
        super.setupRecipe(recipe);
    }

    @Override
    protected void completeRecipe() {
        completions++;
        super.completeRecipe();
    }

    @Override
    protected boolean drawEnergy(long eut, boolean simulate) {
        boolean accepted = super.drawEnergy(eut, simulate);
        if (accepted && !simulate) stored -= eut;
        return accepted;
    }
}

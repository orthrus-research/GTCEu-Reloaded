package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.function.Predicate;

class RecipeCandidateTestLogic extends AbstractRecipeLogic {

    Predicate<Recipe> eligibility = recipe -> true;
    boolean finalAllowed = true;
    int finalChecks;
    int preparations;
    int searches;

    RecipeCandidateTestLogic(RecipeCandidateTestMachine machine, RecipeMap<?> map) {
        super(machine, map);
    }

    @Override
    public boolean isRecipeEligible(Recipe recipe) {
        return super.isRecipeEligible(recipe) && eligibility.test(recipe);
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
        return super.prepareRecipe(recipe);
    }

    @Override
    protected Recipe findRecipe(long voltage, IItemHandlerModifiable items, IMultipleTankHandler fluids) {
        searches++;
        return super.findRecipe(voltage, items, fluids);
    }

    @Override
    protected long getEnergyInputPerSecond() {
        return Long.MAX_VALUE;
    }

    @Override
    protected long getEnergyStored() {
        return Long.MAX_VALUE;
    }

    @Override
    protected long getEnergyCapacity() {
        return Long.MAX_VALUE;
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        return true;
    }

    @Override
    public long getMaxVoltage() {
        return 32;
    }
}

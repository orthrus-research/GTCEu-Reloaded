package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;

import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;

final class RecipeCandidateDistinctLogic extends MultiblockRecipeLogic {

    final List<IItemHandlerModifiable> buses;
    int preparations;

    RecipeCandidateDistinctLogic(RecipeCandidateTestController controller, List<IItemHandlerModifiable> buses) {
        super(controller);
        this.buses = buses;
    }

    @Override
    protected List<IItemHandlerModifiable> getInputBuses() {
        return buses;
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        return new ItemHandlerList(buses);
    }

    @Override
    protected IMultipleTankHandler getInputTank() {
        return new FluidTankList(false);
    }

    @Override
    public long getMaxVoltage() {
        return 32;
    }

    @Override
    protected boolean prepareRecipeDistinct(Recipe recipe) {
        preparations++;
        return recipe.matches(true, currentDistinctInputBus, getInputTank(currentDistinctInputBus));
    }

    void runWhenReady() {
        if (shouldSearchForRecipes()) trySearchNewRecipeDistinct();
    }
}

package gregtech.api.capability.impl;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalComputationReceiver;
import gregtech.api.recipes.RecipeMap;

import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class PendingComputationTestController extends RecipeFailureTestController
                                       implements IOpticalComputationReceiver, IOpticalComputationProvider {

    final ItemStackHandler products = new ItemStackHandler(1);
    int actualCWU;
    int simulatedCWU;
    int maintenanceCalls;

    PendingComputationTestController(RecipeMap<?> map) {
        super(map);
        IdleRecipeTestMachine.attachHolder(this);
    }

    @Override
    public IItemHandlerModifiable getOutputInventory() {
        return products;
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return this;
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        if (simulate) simulatedCWU++;
        else actualCWU++;
        return Math.min(cwut, 8);
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        return 8;
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        return false;
    }

    @Override
    public void calculateMaintenance() {
        maintenanceCalls++;
    }

    @Override
    public void checkStructurePattern() {}
}

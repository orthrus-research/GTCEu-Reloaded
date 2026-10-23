package gregtech.api.capability.impl;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalComputationReceiver;
import gregtech.api.recipes.RecipeMap;

import java.util.Collection;

final class RecipeFailureComputationController extends RecipeFailureTestController
                                               implements IOpticalComputationReceiver, IOpticalComputationProvider {

    int available;
    int simulatedRequests;
    int actualRequests;

    RecipeFailureComputationController(RecipeMap<?> map) {
        super(map);
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return this;
    }

    @Override
    public int requestCWUt(int requested, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        if (simulate) simulatedRequests++;
        else actualRequests++;
        return Math.min(requested, available);
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        throw new AssertionError("Failure reporting must not add a computation capacity probe");
    }

    @Override
    public boolean canBridge(Collection<IOpticalComputationProvider> seen) {
        throw new AssertionError("Failure reporting must not probe optical bridges");
    }
}

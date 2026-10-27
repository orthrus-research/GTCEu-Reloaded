package gregtech.api.configuration;

import gregtech.api.capability.IOpticalComputationProvider;

import java.util.Collection;

final class PipeTestComputationProvider implements IOpticalComputationProvider {

    private final int capacity;

    PipeTestComputationProvider(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        return Math.min(cwut, this.capacity);
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        return this.capacity;
    }

    @Override
    public boolean canBridge(Collection<IOpticalComputationProvider> seen) {
        return true;
    }
}

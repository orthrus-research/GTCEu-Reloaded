package gregtech.api.configuration;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

final class PipeDistributionFailureTarget extends FluidTank {

    final IllegalStateException failure = new IllegalStateException("Injected real fluid acceptance failure");
    private final Runnable beforeFailure;
    int actualFills;

    PipeDistributionFailureTarget(Runnable beforeFailure) {
        super(20_000);
        this.beforeFailure = beforeFailure;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (!doFill) return super.fill(resource, false);
        this.actualFills++;
        this.beforeFailure.run();
        throw this.failure;
    }
}

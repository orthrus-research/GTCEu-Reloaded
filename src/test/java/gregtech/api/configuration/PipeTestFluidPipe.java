package gregtech.api.configuration;

import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;

import net.minecraftforge.fluids.FluidTank;

import org.jetbrains.annotations.Nullable;

final class PipeTestFluidPipe extends TileEntityFluidPipeTickable {

    int dirtyNotifications;
    /** Replaces the channel tanks so tests can interpose on the pipe's own drain during distribution. */
    @Nullable
    FluidTank[] tanksOverride;

    @Override
    public void markDirty() {
        this.dirtyNotifications++;
        super.markDirty();
    }

    @Override
    public FluidTank[] getFluidTanks() {
        return tanksOverride != null ? tanksOverride : super.getFluidTanks();
    }
}

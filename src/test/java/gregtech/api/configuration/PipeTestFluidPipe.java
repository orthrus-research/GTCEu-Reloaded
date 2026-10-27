package gregtech.api.configuration;

import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;

final class PipeTestFluidPipe extends TileEntityFluidPipeTickable {

    int dirtyNotifications;

    @Override
    public void markDirty() {
        this.dirtyNotifications++;
        super.markDirty();
    }
}

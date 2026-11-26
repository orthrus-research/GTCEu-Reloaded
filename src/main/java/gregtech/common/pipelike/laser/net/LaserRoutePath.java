package gregtech.common.pipelike.laser.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.ILaserContainer;
import gregtech.common.pipelike.beam.net.BeamRoutePath;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaserRoutePath extends BeamRoutePath<TileEntityLaserPipe> {

    public LaserRoutePath(TileEntityLaserPipe targetPipe, EnumFacing faceToHandler, int distance) {
        super(targetPipe, faceToHandler, distance);
    }

    /**
     * Gets the current face to handler
     *
     * @return The face to handler
     */
    @NotNull
    public EnumFacing getFaceToHandler() {
        return getTargetFacing();
    }

    /**
     * Gets the handler if it exists
     *
     * @return the handler
     */
    @Nullable
    public ILaserContainer getHandler() {
        return getTargetCapability(GregtechTileCapabilities.CAPABILITY_LASER);
    }
}

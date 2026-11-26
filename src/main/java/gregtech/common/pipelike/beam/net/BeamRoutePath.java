package gregtech.common.pipelike.beam.net;

import gregtech.api.pipenet.IRoutePath;
import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

/**
 * The way along a beam line, from the pipe a request entered to the pipe beside the line's endpoint. The family's
 * route reads the endpoint's capability.
 */
public abstract class BeamRoutePath<T extends TileEntityBeamPipe<?, ?, ?>> implements IRoutePath<T> {

    private final T targetPipe;
    private final EnumFacing faceToHandler;
    private final int distance;

    protected BeamRoutePath(T targetPipe, EnumFacing faceToHandler, int distance) {
        this.targetPipe = targetPipe;
        this.faceToHandler = faceToHandler;
        this.distance = distance;
    }

    @NotNull
    @Override
    public T getTargetPipe() {
        return targetPipe;
    }

    @NotNull
    @Override
    public EnumFacing getTargetFacing() {
        return faceToHandler;
    }

    /**
     * Gets the manhattan distance traveled during walking
     *
     * @return The distance in blocks
     */
    @Override
    public int getDistance() {
        return distance;
    }
}

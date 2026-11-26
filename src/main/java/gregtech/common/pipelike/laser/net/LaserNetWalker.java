package gregtech.common.pipelike.laser.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.common.pipelike.beam.net.BeamNetWalker;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public class LaserNetWalker extends BeamNetWalker<TileEntityLaserPipe, LaserRoutePath> {

    public static final LaserRoutePath FAILED_MARKER = new LaserRoutePath(null, null, 0);

    private static final EnumFacing[] X_AXIS_FACINGS = { EnumFacing.WEST, EnumFacing.EAST };
    private static final EnumFacing[] Y_AXIS_FACINGS = { EnumFacing.UP, EnumFacing.DOWN };
    private static final EnumFacing[] Z_AXIS_FACINGS = { EnumFacing.NORTH, EnumFacing.SOUTH };

    protected LaserNetWalker(World world, BlockPos sourcePipe, int distance) {
        super(world, sourcePipe, distance);
    }

    @Nullable
    public static LaserRoutePath createNetData(World world, BlockPos sourcePipe, EnumFacing faceToSourceHandler) {
        return walk(new LaserNetWalker(world, sourcePipe, 1), sourcePipe, faceToSourceHandler, FAILED_MARKER);
    }

    @Override
    protected LaserNetWalker createWalker(World world, BlockPos pos, int walkedBlocks) {
        return new LaserNetWalker(world, pos, walkedBlocks);
    }

    /** A laser line runs along the axis of the face it was entered from. */
    @Override
    protected EnumFacing[] getSurroundingPipeSides() {
        return switch (getFacingToHandler().getAxis()) {
            case X -> X_AXIS_FACINGS;
            case Y -> Y_AXIS_FACINGS;
            case Z -> Z_AXIS_FACINGS;
        };
    }

    @Override
    protected boolean isEndpoint(TileEntity tile, EnumFacing side) {
        return tile.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, side) != null;
    }

    @Override
    protected LaserRoutePath createRoutePath(TileEntityLaserPipe targetPipe, EnumFacing faceToHandler, int distance) {
        return new LaserRoutePath(targetPipe, faceToHandler, distance);
    }

    @Override
    protected Class<TileEntityLaserPipe> getBasePipeClass() {
        return TileEntityLaserPipe.class;
    }
}

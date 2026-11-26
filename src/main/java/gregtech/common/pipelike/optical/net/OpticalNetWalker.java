package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.common.pipelike.beam.net.BeamNetWalker;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public class OpticalNetWalker extends BeamNetWalker<TileEntityOpticalPipe, OpticalRoutePath> {

    public static final OpticalRoutePath FAILED_MARKER = new OpticalRoutePath(null, null, 0);

    protected OpticalNetWalker(World world, BlockPos sourcePipe, int distance) {
        super(world, sourcePipe, distance);
    }

    @Nullable
    public static OpticalRoutePath createNetData(World world, BlockPos sourcePipe, EnumFacing faceToSourceHandler) {
        return walk(new OpticalNetWalker(world, sourcePipe, 1), sourcePipe, faceToSourceHandler, FAILED_MARKER);
    }

    @Override
    protected OpticalNetWalker createWalker(World world, BlockPos pos, int walkedBlocks) {
        return new OpticalNetWalker(world, pos, walkedBlocks);
    }

    @Override
    protected boolean isEndpoint(TileEntity tile, EnumFacing side) {
        return tile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, side) ||
                tile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, side);
    }

    @Override
    protected OpticalRoutePath createRoutePath(TileEntityOpticalPipe targetPipe, EnumFacing faceToHandler,
                                               int distance) {
        return new OpticalRoutePath(targetPipe, faceToHandler, distance);
    }

    @Override
    protected Class<TileEntityOpticalPipe> getBasePipeClass() {
        return TileEntityOpticalPipe.class;
    }
}

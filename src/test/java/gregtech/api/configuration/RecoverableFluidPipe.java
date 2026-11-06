package gregtech.api.configuration;

import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class RecoverableFluidPipe extends TileEntityFluidPipeTickable {

    void recover(World world, BlockPos position) {
        recoverPipeNetOnLoad(world, position);
    }
}

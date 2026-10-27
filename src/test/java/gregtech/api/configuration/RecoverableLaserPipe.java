package gregtech.api.configuration;

import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class RecoverableLaserPipe extends TileEntityLaserPipe {

    void recover(World loadedWorld, BlockPos loadedPos) {
        recoverPipeNetOnLoad(loadedWorld, loadedPos);
    }
}

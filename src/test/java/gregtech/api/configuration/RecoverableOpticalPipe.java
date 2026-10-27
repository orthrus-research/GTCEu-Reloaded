package gregtech.api.configuration;

import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class RecoverableOpticalPipe extends TileEntityOpticalPipe {

    void recover(World loadedWorld, BlockPos loadedPos) {
        recoverPipeNetOnLoad(loadedWorld, loadedPos);
    }
}

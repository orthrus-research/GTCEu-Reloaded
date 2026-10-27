package gregtech.api.configuration;

import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipe;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class RecoverableItemPipe extends TileEntityItemPipe {

    void recover(World loadedWorld, BlockPos loadedPos) {
        recoverPipeNetOnLoad(loadedWorld, loadedPos);
    }
}

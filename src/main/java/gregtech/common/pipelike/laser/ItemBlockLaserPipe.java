package gregtech.common.pipelike.laser;

import gregtech.api.pipenet.block.BlockPipe;
import gregtech.common.pipelike.beam.ItemBlockBeamPipe;

public class ItemBlockLaserPipe extends ItemBlockBeamPipe<LaserPipeType, LaserPipeProperties> {

    public ItemBlockLaserPipe(BlockPipe<LaserPipeType, LaserPipeProperties, ?> block) {
        super(block, "tile.laser_pipe_normal.tooltip1");
    }
}

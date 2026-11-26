package gregtech.common.pipelike.optical;

import gregtech.common.pipelike.beam.ItemBlockBeamPipe;

public class ItemBlockOpticalPipe extends ItemBlockBeamPipe<OpticalPipeType, OpticalPipeProperties> {

    public ItemBlockOpticalPipe(BlockOpticalPipe block) {
        super(block, "tile.optical_pipe_normal.tooltip1");
    }
}

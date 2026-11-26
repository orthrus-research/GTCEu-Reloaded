package gregtech.common.pipelike.optical.net;

import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.beam.net.BeamPipeNet;
import gregtech.common.pipelike.optical.OpticalPipeProperties;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class OpticalPipeNet extends BeamPipeNet<OpticalPipeProperties, OpticalRoutePath> {

    public OpticalPipeNet(WorldPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
        super(world, OpticalPipeProperties.INSTANCE);
    }

    @Override
    protected OpticalRoutePath createNetData(World world, BlockPos pipePos, EnumFacing facing) {
        return OpticalNetWalker.createNetData(world, pipePos, facing);
    }

    @Override
    protected OpticalRoutePath getFailedMarker() {
        return OpticalNetWalker.FAILED_MARKER;
    }
}

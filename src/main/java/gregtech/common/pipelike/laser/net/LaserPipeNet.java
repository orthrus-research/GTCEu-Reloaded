package gregtech.common.pipelike.laser.net;

import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.beam.net.BeamPipeNet;
import gregtech.common.pipelike.laser.LaserPipeProperties;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public class LaserPipeNet extends BeamPipeNet<LaserPipeProperties, LaserRoutePath> {

    public LaserPipeNet(WorldPipeNet<LaserPipeProperties, ? extends PipeNet<LaserPipeProperties>> world) {
        super(world, LaserPipeProperties.INSTANCE);
    }

    /** A laser line runs along the axis of the face it is entered from, so an unsided query has no route. */
    @Nullable
    @Override
    public LaserRoutePath getNetData(BlockPos pipePos, @Nullable EnumFacing facing) {
        if (facing == null) return null;
        return super.getNetData(pipePos, facing);
    }

    @Override
    protected LaserRoutePath createNetData(World world, BlockPos pipePos, EnumFacing facing) {
        return LaserNetWalker.createNetData(world, pipePos, facing);
    }

    @Override
    protected LaserRoutePath getFailedMarker() {
        return LaserNetWalker.FAILED_MARKER;
    }
}

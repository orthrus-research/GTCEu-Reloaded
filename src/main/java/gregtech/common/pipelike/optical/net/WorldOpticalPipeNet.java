package gregtech.common.pipelike.optical.net;

import gregtech.common.pipelike.beam.net.WorldBeamPipeNet;
import gregtech.common.pipelike.optical.OpticalPipeProperties;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class WorldOpticalPipeNet extends WorldBeamPipeNet<OpticalPipeProperties, OpticalPipeNet> {

    private static final String DATA_ID = "gregtech.optical_pipe_net";

    public WorldOpticalPipeNet(String name) {
        super(name);
    }

    @NotNull
    public static WorldOpticalPipeNet getWorldPipeNet(@NotNull World world) {
        return getOrCreate(world, WorldOpticalPipeNet.class, DATA_ID, WorldOpticalPipeNet::new, "Optical");
    }

    @Override
    protected OpticalPipeNet createNetInstance() {
        return new OpticalPipeNet(this);
    }
}

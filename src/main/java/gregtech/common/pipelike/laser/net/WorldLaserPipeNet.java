package gregtech.common.pipelike.laser.net;

import gregtech.common.pipelike.beam.net.WorldBeamPipeNet;
import gregtech.common.pipelike.laser.LaserPipeProperties;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class WorldLaserPipeNet extends WorldBeamPipeNet<LaserPipeProperties, LaserPipeNet> {

    private static final String DATA_ID = "gregtech.laser_pipe_net";

    public WorldLaserPipeNet(String name) {
        super(name);
    }

    @NotNull
    public static WorldLaserPipeNet getWorldPipeNet(@NotNull World world) {
        return getOrCreate(world, WorldLaserPipeNet.class, DATA_ID, WorldLaserPipeNet::new, "Laser");
    }

    @Override
    protected LaserPipeNet createNetInstance() {
        return new LaserPipeNet(this);
    }
}

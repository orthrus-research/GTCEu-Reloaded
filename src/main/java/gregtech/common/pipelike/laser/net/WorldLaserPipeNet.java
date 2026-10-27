package gregtech.common.pipelike.laser.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.util.GTLog;
import gregtech.common.pipelike.laser.LaserPipeProperties;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class WorldLaserPipeNet extends WorldPipeNet<LaserPipeProperties, LaserPipeNet> {

    private static final String DATA_ID = "gregtech.laser_pipe_net";

    public WorldLaserPipeNet(String name) {
        super(name);
    }

    @NotNull
    public static WorldLaserPipeNet getWorldPipeNet(@NotNull World world) {
        WorldLaserPipeNet netWorldData = (WorldLaserPipeNet) world.getPerWorldStorage()
                .getOrLoadData(WorldLaserPipeNet.class, DATA_ID);
        if (netWorldData == null) {
            netWorldData = new WorldLaserPipeNet(DATA_ID);
            world.getPerWorldStorage().setData(DATA_ID, netWorldData);
            if (world.provider.getDimension() != 0 && world.loadData(WorldLaserPipeNet.class, DATA_ID) != null) {
                GTLog.logger.warn("Laser pipe topology for dimension {} now uses dimension-local storage. " +
                        "Legacy shared topology is preserved without assigning ambiguous nodes; loaded pipes " +
                        "will recover missing local membership. See docs/pipenet-compatibility.md before downgrading.",
                        world.provider.getDimension());
            }
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }

    @Override
    protected LaserPipeNet createNetInstance() {
        return new LaserPipeNet(this);
    }
}

package gregtech.common.pipelike.optical.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.util.GTLog;
import gregtech.common.pipelike.optical.OpticalPipeProperties;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class WorldOpticalPipeNet extends WorldPipeNet<OpticalPipeProperties, OpticalPipeNet> {

    private static final String DATA_ID = "gregtech.optical_pipe_net";

    public WorldOpticalPipeNet(String name) {
        super(name);
    }

    @NotNull
    public static WorldOpticalPipeNet getWorldPipeNet(@NotNull World world) {
        WorldOpticalPipeNet netWorldData = (WorldOpticalPipeNet) world.getPerWorldStorage()
                .getOrLoadData(WorldOpticalPipeNet.class, DATA_ID);
        if (netWorldData == null) {
            netWorldData = new WorldOpticalPipeNet(DATA_ID);
            world.getPerWorldStorage().setData(DATA_ID, netWorldData);
            if (world.provider.getDimension() != 0 && world.loadData(WorldOpticalPipeNet.class, DATA_ID) != null) {
                GTLog.logger.warn("Optical pipe topology for dimension {} now uses dimension-local storage. " +
                        "Legacy shared topology is preserved without assigning ambiguous nodes; loaded pipes " +
                        "will recover missing local membership. See docs/pipenet-compatibility.md before downgrading.",
                        world.provider.getDimension());
            }
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }

    @Override
    protected OpticalPipeNet createNetInstance() {
        return new OpticalPipeNet(this);
    }
}

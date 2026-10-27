package gregtech.common.pipelike.itempipe.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;

import java.io.File;

public class WorldItemPipeNet extends WorldPipeNet<ItemPipeProperties, ItemPipeNet> {

    private static final String DATA_ID = "gregtech.item_pipe_net";

    public static WorldItemPipeNet getWorldPipeNet(World world) {
        WorldItemPipeNet netWorldData = (WorldItemPipeNet) world.getPerWorldStorage()
                .getOrLoadData(WorldItemPipeNet.class, DATA_ID);
        if (netWorldData == null) {
            netWorldData = new WorldItemPipeNet(DATA_ID);
            world.getPerWorldStorage().setData(DATA_ID, netWorldData);
            if (world.provider.getDimension() != 0) {
                ISaveHandler saveHandler = world.getSaveHandler();
                File legacyFile = saveHandler == null ? null : saveHandler.getMapFileFromName(DATA_ID);
                // Loading the old global object here would dirty priority aliases and rewrite ambiguous data.
                if (legacyFile != null && legacyFile.isFile()) {
                    GTLog.logger.warn("Item pipe topology for dimension {} now uses dimension-local storage. " +
                            "Legacy shared topology is preserved without assigning ambiguous nodes; loaded pipes " +
                            "will recover missing local membership. See docs/pipenet-compatibility.md before downgrading.",
                            world.provider.getDimension());
                }
            }
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }

    public WorldItemPipeNet(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        for (ItemPipeNet pipeNet : this.pipeNets) {
            if (pipeNet.needsCodecNormalization()) {
                markDirty();
                break;
            }
        }
    }

    @Override
    protected ItemPipeNet createNetInstance() {
        return new ItemPipeNet(this);
    }
}

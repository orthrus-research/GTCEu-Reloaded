package gregtech.common.pipelike.beam.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.util.GTLog;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * A beam family's topology in one dimension, kept in that dimension's own storage.
 */
public abstract class WorldBeamPipeNet<NodeDataType,
        NetType extends BeamPipeNet<NodeDataType, ?>> extends WorldPipeNet<NodeDataType, NetType> {

    protected WorldBeamPipeNet(String name) {
        super(name);
    }

    /**
     * Loads the family's topology for the world's dimension from that dimension's storage, or creates it there.
     * Topology left in the shared storage by an older version is kept but not taken over, since it cannot tell which
     * dimension its nodes belong to; loaded pipes recover their membership instead.
     *
     * @param family the family's name in the log, such as {@code "Laser"}
     */
    @NotNull
    protected static <W extends WorldBeamPipeNet<?, ?>> W getOrCreate(@NotNull World world, @NotNull Class<W> type,
                                                                      @NotNull String dataId,
                                                                      @NotNull Function<String, W> factory,
                                                                      @NotNull String family) {
        W netWorldData = type.cast(world.getPerWorldStorage().getOrLoadData(type, dataId));
        if (netWorldData == null) {
            netWorldData = factory.apply(dataId);
            world.getPerWorldStorage().setData(dataId, netWorldData);
            if (world.provider.getDimension() != 0 && world.loadData(type, dataId) != null) {
                GTLog.logger.warn("{} pipe topology for dimension {} now uses dimension-local storage. " +
                        "Legacy shared topology is preserved without assigning ambiguous nodes; loaded pipes " +
                        "will recover missing local membership. See docs/pipenet-compatibility.md before downgrading.",
                        family, world.provider.getDimension());
            }
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }
}

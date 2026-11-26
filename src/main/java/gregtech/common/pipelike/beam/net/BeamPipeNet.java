package gregtech.common.pipelike.beam.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.util.FacingPos;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A connected beam line. Its nodes all share one node data, which is not saved, and it caches the route from each
 * pipe face it was asked about. The family's net walks a route with its walker.
 */
public abstract class BeamPipeNet<NodeDataType, RouteType extends BeamRoutePath<?>> extends PipeNet<NodeDataType> {

    private final Map<FacingPos, RouteType> netData = new Object2ObjectOpenHashMap<>();
    private final NodeDataType nodeData;
    private long lastRouteRefresh;
    // world time the pipes were last marked active; a mark lasts 100 ticks, so once a second is enough
    private long lastActiveMark;
    private boolean activeMarked;
    private boolean routeCacheInitialized;

    protected BeamPipeNet(WorldPipeNet<NodeDataType, ? extends PipeNet<NodeDataType>> world, NodeDataType nodeData) {
        super(world);
        this.nodeData = nodeData;
    }

    /** Walks the line from a face of the pipe at {@code pipePos}, with the family walker's {@code createNetData}. */
    protected abstract RouteType createNetData(World world, BlockPos pipePos, EnumFacing facing);

    /** The route {@link #createNetData} returns when the walk failed. */
    protected abstract RouteType getFailedMarker();

    /**
     * @return whether the pipes should be marked active now; true at most once a second
     */
    public boolean shouldMarkActive(long worldTime) {
        if (activeMarked && worldTime >= lastActiveMark && worldTime - lastActiveMark < 20) return false;
        this.activeMarked = true;
        this.lastActiveMark = worldTime;
        return true;
    }

    @Nullable
    public RouteType getNetData(BlockPos pipePos, EnumFacing facing) {
        long now = getWorldData() == null ? 0 : getWorldData().getTotalWorldTime();
        if (!routeCacheInitialized || now < lastRouteRefresh || now - lastRouteRefresh >= 200) {
            netData.clear();
            this.lastRouteRefresh = now;
            this.routeCacheInitialized = true;
        }
        FacingPos key = new FacingPos(pipePos.toImmutable(), facing);
        if (netData.containsKey(key)) {
            return netData.get(key);
        }
        RouteType data = createNetData(getWorldData(), pipePos, facing);
        if (data == getFailedMarker()) {
            // walker failed, don't cache, so it tries again on next insertion
            return null;
        }
        netData.put(key, data);
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        netData.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        netData.clear();
    }

    @Override
    public void onChunkUnload() {
        netData.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<NodeDataType>> transferredNodes,
                                    PipeNet<NodeDataType> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((BeamPipeNet<?, ?>) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(NodeDataType data, NBTTagCompound tagCompound) {}

    @Override
    protected NodeDataType readNodeData(NBTTagCompound tagCompound) {
        return nodeData;
    }
}

package gregtech.common.pipelike.laser.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.util.FacingPos;
import gregtech.common.pipelike.laser.LaserPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LaserPipeNet extends PipeNet<LaserPipeProperties> {

    private final Map<FacingPos, LaserRoutePath> netData = new Object2ObjectOpenHashMap<>();
    private long lastRouteRefresh;
    private boolean routeCacheInitialized;

    public LaserPipeNet(WorldPipeNet<LaserPipeProperties, ? extends PipeNet<LaserPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public LaserRoutePath getNetData(BlockPos pipePos, EnumFacing facing) {
        if (facing == null) return null;
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
        LaserRoutePath data = LaserNetWalker.createNetData(getWorldData(), pipePos, facing);
        if (data == LaserNetWalker.FAILED_MARKER) {
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
    protected void transferNodeData(Map<BlockPos, Node<LaserPipeProperties>> transferredNodes,
                                    PipeNet<LaserPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((LaserPipeNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(LaserPipeProperties nodeData, NBTTagCompound tagCompound) {}

    @Override
    protected LaserPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return LaserPipeProperties.INSTANCE;
    }
}

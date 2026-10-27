package gregtech.common.pipelike.cable.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.WireProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class EnergyNet extends PipeNet<WireProperties> {

    private long lastEnergyFluxPerSec;
    private long energyFluxPerSec;
    private long lastTime;
    private long lastRouteRefresh;
    private boolean routeCacheInitialized;

    private final Map<BlockPos, List<EnergyRoutePath>> NET_DATA = new Object2ObjectOpenHashMap<>();

    protected EnergyNet(WorldPipeNet<WireProperties, EnergyNet> world) {
        super(world);
    }

    public List<EnergyRoutePath> getNetData(BlockPos pipePos) {
        refreshRouteCache();
        List<EnergyRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = EnergyNetWalker.createNetData(getWorldData(), pipePos);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(EnergyRoutePath::getDistance));
            NET_DATA.put(pipePos.toImmutable(), data);
        }
        return data;
    }

    private void refreshRouteCache() {
        World world = getWorldData();
        long now = world == null ? 0 : world.getTotalWorldTime();
        if (!routeCacheInitialized || now < lastRouteRefresh || now - lastRouteRefresh >= 200) {
            NET_DATA.clear();
            this.lastRouteRefresh = now;
            this.routeCacheInitialized = true;
        }
    }

    private void clearRoutes() {
        NET_DATA.clear();
        this.routeCacheInitialized = false;
    }

    public long getEnergyFluxPerSec() {
        World world = getWorldData();
        if (world != null && !world.isRemote && (world.getTotalWorldTime() - lastTime) >= 20) {
            lastTime = world.getTotalWorldTime();
            clearCache();
        }
        return lastEnergyFluxPerSec;
    }

    public void addEnergyFluxPerSec(long energy) {
        energyFluxPerSec += energy;
    }

    public void clearCache() {
        lastEnergyFluxPerSec = energyFluxPerSec;
        energyFluxPerSec = 0;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        clearRoutes();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        clearRoutes();
    }

    @Override
    public void onChunkUnload() {
        clearRoutes();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        clearRoutes();
        ((EnergyNet) parentNet).clearRoutes();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("voltage", nodeData.getVoltage());
        tagCompound.setInteger("amperage", nodeData.getAmperage());
        tagCompound.setInteger("loss", nodeData.getLossPerBlock());
    }

    @Override
    protected WireProperties readNodeData(NBTTagCompound tagCompound) {
        int voltage = tagCompound.getInteger("voltage");
        int amperage = tagCompound.getInteger("amperage");
        int lossPerBlock = tagCompound.getInteger("loss");
        return new WireProperties(voltage, amperage, lossPerBlock);
    }
}

package gregtech.common.pipelike.itempipe.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.ItemPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemPipeNet extends PipeNet<ItemPipeProperties> {

    public static final int ROUTE_REFRESH_TICKS = 200;

    private final Map<BlockPos, ItemRouteCache[]> NET_DATA = new HashMap<>();
    private boolean needsCodecNormalization;

    public ItemPipeNet(WorldPipeNet<ItemPipeProperties, ? extends PipeNet<ItemPipeProperties>> world) {
        super(world);
    }

    public List<ItemRoutePath> getNetData(BlockPos pipePos, EnumFacing facing) {
        int side = facing == null ? 6 : facing.getIndex();
        long now = getWorldData().getTotalWorldTime();
        ItemRouteCache[] bySide = NET_DATA.get(pipePos);
        ItemRouteCache cached = bySide == null ? null : bySide[side];
        if (cached == null || now < cached.createdAt || now - cached.createdAt >= ROUTE_REFRESH_TICKS) {
            List<ItemRoutePath> data = ItemNetWalker.createNetData(getWorldData(), pipePos, facing);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(inv -> inv.getProperties().getPriority()));
            if (bySide == null) {
                bySide = new ItemRouteCache[7];
                NET_DATA.put(pipePos.toImmutable(), bySide);
            }
            cached = new ItemRouteCache(data, now);
            bySide[side] = cached;
        }
        return cached.routes;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
        worldData.invalidateRoutes();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        NET_DATA.clear();
        worldData.invalidateRoutes();
    }

    @Override
    public void onChunkUnload() {
        NET_DATA.clear();
        worldData.invalidateRoutes();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<ItemPipeProperties>> transferredNodes,
                                    PipeNet<ItemPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((ItemPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(ItemPipeProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("Resistance", nodeData.getPriority());
        tagCompound.setInteger("Range", nodeData.getPriority());
        tagCompound.setFloat("Rate", nodeData.getTransferRate());
    }

    @Override
    protected ItemPipeProperties readNodeData(NBTTagCompound tagCompound) {
        if (tagCompound.hasKey("Rate") && !tagCompound.hasKey("Rate", NBT.TAG_ANY_NUMERIC)) {
            throw new IllegalArgumentException("Saved item pipe rate is not numeric");
        }
        if ((tagCompound.hasKey("Resistance") || tagCompound.hasKey("Range")) &&
                !tagCompound.hasKey("Resistance", NBT.TAG_INT) && !tagCompound.hasKey("Range", NBT.TAG_INT)) {
            throw new IllegalArgumentException("Saved item pipe priority has no valid integer key");
        }
        int priority = tagCompound.hasKey("Resistance", NBT.TAG_INT) ? tagCompound.getInteger("Resistance") :
                tagCompound.getInteger("Range");
        float rate = tagCompound.getFloat("Rate");
        if (!Float.isFinite(rate) || rate < 0) {
            throw new IllegalArgumentException("Invalid saved item pipe transfer rate: " + rate);
        }
        if (!tagCompound.hasKey("Resistance", NBT.TAG_INT) || !tagCompound.hasKey("Range", NBT.TAG_INT) ||
                tagCompound.getInteger("Resistance") != tagCompound.getInteger("Range")) {
            this.needsCodecNormalization = true;
        }
        return new ItemPipeProperties(priority, rate);
    }

    boolean needsCodecNormalization() {
        return this.needsCodecNormalization;
    }
}

package gregtech.api.pipenet;

import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class WorldPipeNet<NodeDataType, T extends PipeNet<NodeDataType>> extends WorldSavedData {

    private WeakReference<World> worldRef = new WeakReference<>(null);
    private boolean invalidatingRoutes;
    private boolean acceptedSavedData;
    private NBTTagCompound quarantinedData;
    protected List<T> pipeNets = new ArrayList<>();
    protected final Map<ChunkPos, List<T>> pipeNetsByChunk = new HashMap<>();

    public WorldPipeNet(String name) {
        super(name);
    }

    public World getWorld() {
        return this.worldRef.get();
    }

    /** True when a rejected initial record must be preserved until an explicit validated replacement. */
    public boolean hasQuarantinedData() {
        return this.quarantinedData != null;
    }

    protected void setWorldAndInit(World world) {
        if (world != this.worldRef.get()) {
            this.worldRef = new WeakReference<>(world);
            onWorldSet();
        }
    }

    public static @NotNull String getDataID(@NotNull final String baseID, @NotNull final World world) {
        // noinspection ConstantValue
        if (world == null || world.isRemote) {
            GTLog.logger.error("WorldPipeNet should only be created on the server!", new Throwable());
            // noinspection ConstantValue
            if (world == null) {
                return baseID;
            }
        }

        int dimension = world.provider.getDimension();
        return dimension == 0 ? baseID : baseID + '.' + dimension;
    }

    protected void onWorldSet() {
        this.pipeNets.forEach(PipeNet::onNodeConnectionsUpdate);
        invalidateRoutes();
    }

    /**
     * Invalidates routes in this family, including routes that cross painted component boundaries.
     * A snapshot permits an extension hook to change component membership during notification.
     */
    public void invalidateRoutes() {
        if (this.invalidatingRoutes) {
            return;
        }
        this.invalidatingRoutes = true;
        try {
            for (T pipeNet : new ArrayList<>(this.pipeNets)) {
                if (pipeNet.isValid()) {
                    pipeNet.onPipeConnectionsUpdate();
                }
            }
        } finally {
            this.invalidatingRoutes = false;
        }
    }

    /** Preserves a changed pipe's explicit local callback while invalidating crossing family routes. */
    public void invalidateRoutesAt(BlockPos nodePos) {
        if (getNetFromPos(nodePos) != null) {
            invalidateRoutes();
        }
    }

    public void addNode(BlockPos nodePos, NodeDataType nodeData, int mark, int openConnections, boolean isActive) {
        Objects.requireNonNull(nodeData);
        if ((openConnections & ~63) != 0) {
            throw new IllegalArgumentException("Pipe connection mask is outside the six faces: " + openConnections);
        }
        nodePos = nodePos.toImmutable();
        T existingNet = getNetFromPos(nodePos);
        if (existingNet != null) {
            Node<NodeDataType> existing = existingNet.getNodeAt(nodePos);
            if (Objects.equals(existing.data, nodeData) && existing.mark == mark &&
                    existing.openConnections == openConnections && existing.isActive == isActive) {
                return;
            }
            existingNet.removeNode(nodePos);
        }
        T myPipeNet = null;
        Node<NodeDataType> node = new Node<>(nodeData, openConnections, mark, isActive);
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos offsetPos = nodePos.offset(facing);
            T pipeNet = getNetFromPos(offsetPos);
            Node<NodeDataType> secondNode = pipeNet == null ? null : pipeNet.getAllNodes().get(offsetPos);
            if (pipeNet != null && pipeNet.canAttachNode(nodeData) &&
                    pipeNet.canNodesConnect(secondNode, facing.getOpposite(), node, null)) {
                if (myPipeNet == null) {
                    myPipeNet = pipeNet;
                    myPipeNet.addNode(nodePos, node);
                } else if (myPipeNet != pipeNet) {
                    myPipeNet.uniteNetworks(pipeNet);
                }
            }

        }
        if (myPipeNet == null) {
            myPipeNet = createNetInstance();
            myPipeNet.addNode(nodePos, node);
            addPipeNet(myPipeNet);
            markDirty();
        }
        invalidateRoutes();
    }

    protected void addPipeNetToChunk(ChunkPos chunkPos, T pipeNet) {
        List<T> list = this.pipeNetsByChunk.computeIfAbsent(chunkPos, any -> new ArrayList<>());
        if (!containsIdentity(list, pipeNet)) {
            list.add(pipeNet);
        }
    }

    protected void removePipeNetFromChunk(ChunkPos chunkPos, T pipeNet) {
        List<T> list = this.pipeNetsByChunk.get(chunkPos);
        if (list != null) {
            removeIdentity(list, pipeNet);
            if (list.isEmpty()) {
                this.pipeNetsByChunk.remove(chunkPos);
            }
        }
    }

    public void removeNode(BlockPos nodePos) {
        T pipeNet = getNetFromPos(nodePos);
        if (pipeNet != null) {
            pipeNet.removeNode(nodePos);
            invalidateRoutes();
        }
    }

    public void updateBlockedConnections(BlockPos nodePos, EnumFacing side, boolean isBlocked) {
        T pipeNet = getNetFromPos(nodePos);
        if (pipeNet != null) {
            pipeNet.updateBlockedConnections(nodePos, side, isBlocked);
            invalidateRoutes();
        }
    }

    public void updateMark(BlockPos nodePos, int newMark) {
        T pipeNet = getNetFromPos(nodePos);
        if (pipeNet != null) {
            pipeNet.updateMark(nodePos, newMark);
            invalidateRoutes();
        }
    }

    public T getNetFromPos(BlockPos blockPos) {
        List<T> pipeNetsInChunk = pipeNetsByChunk.getOrDefault(new ChunkPos(blockPos), Collections.emptyList());
        for (T pipeNet : pipeNetsInChunk) {
            if (pipeNet.isValid() && pipeNet.containsNode(blockPos))
                return pipeNet;
        }
        return null;
    }

    protected void addPipeNet(T pipeNet) {
        addPipeNetSilently(pipeNet);
    }

    protected void addPipeNetSilently(T pipeNet) {
        if (containsIdentity(this.pipeNets, pipeNet)) {
            return;
        }
        this.pipeNets.add(pipeNet);
        pipeNet.getContainedChunks().forEach(chunkPos -> addPipeNetToChunk(chunkPos, pipeNet));
        pipeNet.isValid = true;
    }

    protected void removePipeNet(T pipeNet) {
        removeIdentity(this.pipeNets, pipeNet);
        pipeNet.getContainedChunks().forEach(chunkPos -> removePipeNetFromChunk(chunkPos, pipeNet));
        pipeNet.isValid = false;
    }

    protected abstract T createNetInstance();

    private static <N> boolean containsIdentity(List<N> networks, N target) {
        for (N network : networks) {
            if (network == target) return true;
        }
        return false;
    }

    private static <N> void removeIdentity(List<N> networks, N target) {
        for (int index = 0; index < networks.size(); index++) {
            if (networks.get(index) == target) {
                networks.remove(index);
                return;
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        Objects.requireNonNull(nbt);
        boolean preserveRejectedRecord = !this.acceptedSavedData && this.pipeNets.isEmpty() &&
                this.quarantinedData == null;
        try {
            readSavedNetworks(nbt);
            this.acceptedSavedData = true;
            this.quarantinedData = null;
        } catch (RuntimeException exception) {
            if (preserveRejectedRecord) {
                this.quarantinedData = nbt.copy();
                GTLog.logger.error("Pipe network {} rejected malformed initial data. Its complete original NBT " +
                        "is quarantined and will be preserved on save; loaded-tile recovery does not repair " +
                        "this record. Supply an explicit validated replacement after manual inspection.",
                        this.mapName, exception);
            }
            throw exception;
        }
    }

    private void readSavedNetworks(NBTTagCompound nbt) {
        if (nbt.hasKey("PipeNets") && !nbt.hasKey("PipeNets", NBT.TAG_LIST)) {
            throw new IllegalArgumentException("PipeNets must be a list");
        }
        if (nbt.hasKey("PipeNets")) {
            NBTTagList list = (NBTTagList) nbt.getTag("PipeNets");
            if (list.tagCount() != 0 && list.getTagType() != NBT.TAG_COMPOUND) {
                throw new IllegalArgumentException("PipeNets must contain compounds");
            }
        }
        List<T> restoredNets = new ArrayList<>();
        Set<BlockPos> restoredPositions = new HashSet<>();
        NBTTagList allEnergyNets = nbt.getTagList("PipeNets", NBT.TAG_COMPOUND);
        for (int i = 0; i < allEnergyNets.tagCount(); i++) {
            NBTTagCompound pNetTag = allEnergyNets.getCompoundTagAt(i);
            T pipeNet = createNetInstance();
            pipeNet.deserializeNBT(pNetTag);
            for (BlockPos pos : pipeNet.getAllNodes().keySet()) {
                if (!restoredPositions.add(pos)) {
                    throw new IllegalArgumentException("Duplicate pipe position across networks: " + pos);
                }
            }
            restoredNets.add(pipeNet);
        }
        for (T oldNet : new ArrayList<>(this.pipeNets)) {
            removePipeNet(oldNet);
        }
        this.pipeNets = new ArrayList<>();
        this.pipeNetsByChunk.clear();
        restoredNets.forEach(this::addPipeNetSilently);
        invalidateRoutes();
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        if (this.quarantinedData != null) {
            return this.quarantinedData.copy();
        }
        NBTTagList allPipeNets = new NBTTagList();
        for (T pipeNet : pipeNets) {
            NBTTagCompound pNetTag = pipeNet.serializeNBT();
            allPipeNets.appendTag(pNetTag);
        }
        compound.setTag("PipeNets", allPipeNets);
        return compound;
    }
}

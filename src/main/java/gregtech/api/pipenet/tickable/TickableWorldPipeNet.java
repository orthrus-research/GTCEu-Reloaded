package gregtech.api.pipenet.tickable;

import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.stream.Collectors;

public abstract class TickableWorldPipeNet<NodeDataType, T extends PipeNet<NodeDataType> & ITickable>
                                          extends WorldPipeNet<NodeDataType, T> {

    private final Map<T, List<ChunkPos>> loadedChunksByPipeNet = new HashMap<>();
    private final Set<T> tickingPipeNets = new HashSet<>();
    private final Set<T> removeLater = new HashSet<>();

    public TickableWorldPipeNet(String name) {
        super(name);
    }

    private boolean isChunkLoaded(ChunkPos chunkPos) {
        World world = getWorld();
        return world != null && world.isBlockLoaded(new BlockPos(chunkPos.getXStart(), 0, chunkPos.getZStart()));
    }

    protected abstract int getUpdateRate();

    public void update() {
        this.tickingPipeNets.removeAll(this.removeLater);
        this.removeLater.clear();
        World world = getWorld();
        if (world == null) return;
        int updateRate = getUpdateRate();
        if (updateRate <= 0) {
            throw new IllegalStateException("Pipe network update rate must be positive");
        }
        if (world.getTotalWorldTime() % updateRate == 0L) {
            for (T pipeNet : new ArrayList<>(this.tickingPipeNets)) {
                if (pipeNet.isValid() && !this.removeLater.contains(pipeNet)) {
                    pipeNet.update();
                }
            }
        }
    }

    public void onChunkLoaded(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<T> pipeNetsInThisChunk = this.pipeNetsByChunk.get(chunkPos);
        if (pipeNetsInThisChunk == null) return;
        for (T pipeNet : pipeNetsInThisChunk) {
            List<ChunkPos> loadedChunks = getOrCreateChunkListForPipeNet(pipeNet);
            if (loadedChunks.isEmpty()) {
                addToTicking(pipeNet);
            }
            if (!loadedChunks.contains(chunkPos)) {
                loadedChunks.add(chunkPos);
            }
        }
    }

    public void onChunkUnloaded(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<T> pipeNetsInThisChunk = this.pipeNetsByChunk.get(chunkPos);
        if (pipeNetsInThisChunk == null) return;
        for (T pipeNet : pipeNetsInThisChunk) {
            List<ChunkPos> loadedChunks = this.loadedChunksByPipeNet.get(pipeNet);
            if (loadedChunks != null && loadedChunks.contains(chunkPos)) {
                loadedChunks.remove(chunkPos);
                if (loadedChunks.isEmpty()) {
                    removeFromTicking(pipeNet);
                }
            }
        }
    }

    @Override
    protected void onWorldSet() {
        super.onWorldSet();
        this.loadedChunksByPipeNet.clear();
        this.tickingPipeNets.clear();
        this.removeLater.clear();
        for (T pipeNet : this.pipeNets) {
            List<ChunkPos> loadedChunks = getPipeNetLoadedChunks(pipeNet);
            if (!loadedChunks.isEmpty()) {
                this.loadedChunksByPipeNet.put(pipeNet, loadedChunks);
                addToTicking(pipeNet);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.loadedChunksByPipeNet.clear();
        this.tickingPipeNets.clear();
        this.removeLater.clear();
        if (getWorld() != null) {
            onWorldSet();
        }
    }

    @Override
    protected void addPipeNet(T pipeNet) {
        super.addPipeNet(pipeNet);
        List<ChunkPos> loadedChunks = getPipeNetLoadedChunks(pipeNet);
        if (!loadedChunks.isEmpty()) {
            this.loadedChunksByPipeNet.put(pipeNet, loadedChunks);
            addToTicking(pipeNet);
        }
    }

    private List<ChunkPos> getPipeNetLoadedChunks(T pipeNet) {
        return pipeNet.getContainedChunks().stream()
                .filter(this::isChunkLoaded)
                .collect(Collectors.toList());
    }

    @Override
    protected void removePipeNet(T pipeNet) {
        super.removePipeNet(pipeNet);
        if (loadedChunksByPipeNet.containsKey(pipeNet)) {
            removeFromTicking(pipeNet);
        }
    }

    private void removeFromTicking(T pipeNet) {
        this.loadedChunksByPipeNet.remove(pipeNet);
        this.removeLater.add(pipeNet);
    }

    private void addToTicking(T pipeNet) {
        this.removeLater.remove(pipeNet);
        this.tickingPipeNets.add(pipeNet);
    }

    private List<ChunkPos> getOrCreateChunkListForPipeNet(T pipeNet) {
        return this.loadedChunksByPipeNet.computeIfAbsent(pipeNet, k -> new ArrayList<>());
    }

    @Override
    protected void addPipeNetToChunk(ChunkPos chunkPos, T pipeNet) {
        super.addPipeNetToChunk(chunkPos, pipeNet);
        if (isChunkLoaded(chunkPos)) {
            List<ChunkPos> loadedChunks = getOrCreateChunkListForPipeNet(pipeNet);
            if (loadedChunks.isEmpty()) {
                addToTicking(pipeNet);
            }
            if (!loadedChunks.contains(chunkPos)) {
                loadedChunks.add(chunkPos);
            }
        }
    }

    @Override
    protected void removePipeNetFromChunk(ChunkPos chunkPos, T pipeNet) {
        super.removePipeNetFromChunk(chunkPos, pipeNet);
        List<ChunkPos> loadedChunks = this.loadedChunksByPipeNet.get(pipeNet);
        if (loadedChunks != null && loadedChunks.contains(chunkPos)) {
            loadedChunks.remove(chunkPos);
            if (loadedChunks.isEmpty()) {
                removeFromTicking(pipeNet);
            }
        }
    }
}

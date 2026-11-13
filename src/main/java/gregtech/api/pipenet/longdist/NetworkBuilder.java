package gregtech.api.pipenet.longdist;

import gregtech.api.util.function.Task;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Walks long distance pipes from the given starting points and builds their networks.
 * <p>
 * It runs as a {@link Task} on the server thread, a bounded number of positions per tick: the walk reads block
 * states, loads chunks and writes the world's network maps, none of which may be touched from another thread.
 */
public class NetworkBuilder implements Task {

    /** Positions walked per tick; most networks finish within their first tick. */
    private static final int POSITIONS_PER_TICK = 16384;

    private final ObjectList<BlockPos> starts = new ObjectArrayList<>();
    private final LongDistanceNetwork.WorldData worldData;
    private final LongDistanceNetwork originalNetwork;
    private LongDistanceNetwork network;
    private final World world;
    private final ObjectList<BlockPos> currentPoints = new ObjectArrayList<>();
    private final ObjectOpenHashSet<BlockPos> walked = new ObjectOpenHashSet<>();
    private final List<BlockPos> pipes = new ArrayList<>();
    private final List<ILDEndpoint> endpoints = new ArrayList<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final ObjectOpenHashSet<Chunk> loadedChunks = new ObjectOpenHashSet<>();
    private boolean started;
    private boolean walking;
    private boolean cancelled;

    public NetworkBuilder(LongDistanceNetwork.WorldData worldData, LongDistanceNetwork network,
                          Collection<BlockPos> starts) {
        this.worldData = Objects.requireNonNull(worldData);
        this.originalNetwork = Objects.requireNonNull(network);
        this.network = network;
        this.world = worldData.getWorld();
        this.starts.addAll(starts);
    }

    /** Stops this builder before its next step; a newer build of the same network replaces it. */
    void cancel() {
        this.cancelled = true;
    }

    @Override
    public boolean run() {
        if (this.cancelled) {
            finish();
            return false;
        }
        int budget = POSITIONS_PER_TICK;
        while (budget > 0) {
            if (!this.walking) {
                if (this.starts.isEmpty()) {
                    finish();
                    return false;
                }
                BlockPos start = this.starts.remove(0);
                if (this.started) {
                    if (this.worldData.getNetwork(start) == this.originalNetwork) {
                        // this starting point was caught during a previous walk, so it needs no other network
                        continue;
                    }
                    // create a new network, since the current was already calculated
                    this.network = this.network.getPipeType().createNetwork(this.worldData);
                    this.currentPoints.clear();
                    this.walked.clear();
                    this.pipes.clear();
                    this.endpoints.clear();
                }
                this.started = true;
                this.walking = true;
                checkPos(getBlockState(start), start);
                budget--;
                continue;
            }
            if (this.currentPoints.isEmpty()) {
                // the whole net was checked, now send the data to the network
                this.network.setData(this.pipes, this.endpoints);
                this.walking = false;
                continue;
            }
            // get and remove the first stored branch
            BlockPos current = this.currentPoints.remove(0);
            for (EnumFacing facing : EnumFacing.VALUES) {
                this.pos.setPos(current).move(facing);
                if (this.walked.contains(this.pos)) {
                    continue;
                }
                budget--;
                IBlockState blockState = getBlockState(this.pos);
                if (blockState.getBlock().isAir(blockState, this.world, this.pos)) {
                    continue;
                }
                checkPos(blockState, this.pos);
            }
        }
        return true;
    }

    private void finish() {
        IChunkProvider chunkProvider = this.world.getChunkProvider();
        if (chunkProvider instanceof ChunkProviderServer chunkProviderServer) {
            this.loadedChunks.forEach(chunkProviderServer::queueUnload);
        }
        this.loadedChunks.clear();
        this.originalNetwork.onBuilderFinished(this);
    }

    /**
     * Checks a pos for a pipe or a endpoint
     */
    private void checkPos(IBlockState blockState, BlockPos pos) {
        LongDistanceNetwork network = LongDistanceNetwork.get(this.world, pos);
        if (network != null && network != this.network) {
            network.invalidateNetwork(true);
        }
        BlockPos bp = pos.toImmutable();
        this.walked.add(bp);
        ILDNetworkPart part = ILDNetworkPart.tryGet(this.world, pos, blockState);
        if (part != null) {
            this.pipes.add(bp);
            if (part instanceof ILDEndpoint endpoint) this.endpoints.add(endpoint);
            else this.currentPoints.add(bp);
        }
    }

    /**
     * Special method which can get block state which are far away. It temporarily loads the chunk for that.
     */
    private IBlockState getBlockState(BlockPos pos) {
        if (this.world.isOutsideBuildHeight(pos)) return Blocks.AIR.getDefaultState();
        IChunkProvider chunkProvider = this.world.getChunkProvider();
        Chunk chunk = chunkProvider.getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        // chunk is not loaded, try to load it
        if (chunk == null) {
            // don't force generate a chunk
            if (!chunkProvider.isChunkGeneratedAt(pos.getX() >> 4, pos.getZ() >> 4)) {
                return Blocks.AIR.getDefaultState();
            }
            chunk = chunkProvider.provideChunk(pos.getX() >> 4, pos.getZ() >> 4);
            // add loaded chunk to list to unload it later
            this.loadedChunks.add(chunk);
        }
        return chunk.getBlockState(pos);
    }
}

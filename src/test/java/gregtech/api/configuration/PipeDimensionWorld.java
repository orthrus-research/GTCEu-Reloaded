package gregtech.api.configuration;

import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Models Forge's shared global storage and independent dimension-local stores. */
final class PipeDimensionWorld extends DummyWorld {

    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    private final Map<BlockPos, IBlockState> states = new HashMap<>();
    final Set<BlockPos> unloaded = new HashSet<>();
    private ISaveHandler legacySaveHandler;
    private boolean overridesSaveHandler;

    PipeDimensionWorld(int dimension, MapStorage global, MapStorage local) {
        this.provider.setDimension(dimension);
        this.mapStorage = global;
        this.perWorldStorage = local;
    }

    PipeDimensionWorld(int dimension, MapStorage global, MapStorage local, ISaveHandler legacySaveHandler) {
        this(dimension, global, local);
        this.legacySaveHandler = legacySaveHandler;
        this.overridesSaveHandler = true;
    }

    @Override
    public ISaveHandler getSaveHandler() {
        return this.overridesSaveHandler ? this.legacySaveHandler : super.getSaveHandler();
    }

    void put(BlockPos pos, TileEntity tile, IBlockState state) {
        this.tiles.put(pos, tile);
        this.states.put(pos, state);
        tile.setWorld(this);
        tile.setPos(pos);
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return this.unloaded == null || !this.unloaded.contains(pos);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        if (!isBlockLoaded(pos)) throw new AssertionError("Unloaded tile was queried: " + pos);
        return this.tiles.get(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return this.states == null ? Blocks.AIR.getDefaultState() :
                this.states.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity tile) {}
}

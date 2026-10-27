package gregtech.api.configuration;

import gregtech.api.metatileentity.NeighborCacheTileEntityBase;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory world storage; actual machines, pipe setters and WorldPipeNet implementations run unchanged. */
final class ConfigurationTestWorld extends DummyWorld {

    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    private final Map<BlockPos, IBlockState> states = new HashMap<>();
    private final Map<String, WorldSavedData> savedData = new HashMap<>();
    final Set<BlockPos> unloaded = new HashSet<>();

    void put(BlockPos pos, TileEntity tile, IBlockState state) {
        tiles.put(pos, tile);
        states.put(pos, state);
        tile.setWorld(this);
        tile.setPos(pos);
        for (EnumFacing side : EnumFacing.VALUES) {
            TileEntity neighbor = tiles.get(pos.offset(side));
            if (neighbor instanceof NeighborCacheTileEntityBase cached) cached.onNeighborChanged(side.getOpposite());
        }
    }

    @Override
    public void setTileEntity(BlockPos pos, TileEntity tile) {
        put(pos, tile, getBlockState(pos));
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return unloaded == null || !unloaded.contains(pos);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        if (!isBlockLoaded(pos)) throw new AssertionError("Unloaded position was read: " + pos);
        return tiles.get(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return states == null ? Blocks.AIR.getDefaultState() : states.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity tile) {}

    @Override
    public WorldSavedData loadData(Class<? extends WorldSavedData> type, String name) {
        return savedData.get(name);
    }

    @Override
    public void setData(String name, WorldSavedData value) {
        savedData.put(name, value);
    }
}

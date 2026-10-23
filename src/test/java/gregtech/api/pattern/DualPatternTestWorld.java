package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

final class DualPatternTestWorld extends DummyWorld {

    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    private final Map<BlockPos, IBlockState> states = new HashMap<>();

    void put(BlockPos pos, MetaTileEntity part) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(part);
            }
        };
        holder.setWorld(this);
        holder.setPos(pos);
        tiles.put(pos, holder);
        // Pattern cache snapshots require a tile-capable state; ability positions are not casing blocks.
        states.put(pos, Blocks.CHEST.getDefaultState());
    }

    void put(BlockPos pos, IBlockState state) {
        tiles.remove(pos);
        states.put(pos, state);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return tiles.get(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return states == null ? Blocks.AIR.getDefaultState() : states.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return true;
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity tile) {}
}

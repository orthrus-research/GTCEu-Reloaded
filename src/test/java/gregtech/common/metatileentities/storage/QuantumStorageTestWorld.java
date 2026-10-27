package gregtech.common.metatileentities.storage;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.NeighborCacheTileEntityBase;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

final class QuantumStorageTestWorld extends DummyWorld {

    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    int tileReads;

    void put(BlockPos pos, MetaTileEntity machine) {
        QuantumStorageTestHolder holder = new QuantumStorageTestHolder();
        holder.attach(machine);
        holder.setWorld(this);
        holder.setPos(pos);
        tiles.put(pos, holder);
        invalidateNeighbors(pos);
    }

    void remove(BlockPos pos) {
        tiles.remove(pos);
        invalidateNeighbors(pos);
    }

    private void invalidateNeighbors(BlockPos pos) {
        for (EnumFacing side : EnumFacing.VALUES) {
            TileEntity neighbor = tiles.get(pos.offset(side));
            if (neighbor instanceof NeighborCacheTileEntityBase cached) cached.onNeighborChanged(side.getOpposite());
        }
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        this.tileReads++;
        return tiles.get(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return tiles != null && tiles.containsKey(pos) ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState();
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return true;
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity tile) {}
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

final class PatternBufferTestWorld extends DummyWorld {

    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    boolean loaded = true;
    boolean modifiable = true;
    int lookups;

    MetaTileEntityHolder put(BlockPos position, MetaTileEntity part) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(part);
            }
        };
        holder.setWorld(this);
        holder.setPos(position);
        tiles.put(position, holder);
        return holder;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        lookups++;
        return tiles.get(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return Blocks.CHEST.getDefaultState();
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return loaded;
    }

    @Override
    public boolean isBlockModifiable(EntityPlayer player, BlockPos pos) {
        return modifiable;
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity tile) {}
}

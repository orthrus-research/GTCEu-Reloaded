package gregtech.common.pipelike.optical;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.client.renderer.pipe.OpticalPipeRenderer;
import gregtech.common.pipelike.beam.BlockBeamPipe;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockOpticalPipe extends BlockBeamPipe<OpticalPipeType, OpticalPipeProperties, WorldOpticalPipeNet> {

    public BlockOpticalPipe(@NotNull OpticalPipeType pipeType) {
        super(pipeType, OpticalPipeProperties.INSTANCE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(@NotNull World world, BlockPos blockPos) {
        return OpticalPipeRenderer.INSTANCE.getParticleTexture((TileEntityOpticalPipe) world.getTileEntity(blockPos));
    }

    @Override
    public Class<OpticalPipeType> getPipeTypeClass() {
        return OpticalPipeType.class;
    }

    @Override
    public WorldOpticalPipeNet getWorldPipeNet(World world) {
        return WorldOpticalPipeNet.getWorldPipeNet(world);
    }

    @Override
    public TileEntityPipeBase<OpticalPipeType, OpticalPipeProperties> createNewTileEntity(boolean supportsTicking) {
        return new TileEntityOpticalPipe();
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeTile<OpticalPipeType, OpticalPipeProperties> selfTile, EnumFacing side,
                                         @Nullable TileEntity tile) {
        if (tile == null) return false;
        if (tile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, side.getOpposite())) return true;
        return tile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, side.getOpposite());
    }

    @Override
    @NotNull
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return OpticalPipeRenderer.INSTANCE.getBlockRenderType();
    }
}

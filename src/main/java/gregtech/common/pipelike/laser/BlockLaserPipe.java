package gregtech.common.pipelike.laser;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.client.renderer.pipe.LaserPipeRenderer;
import gregtech.common.pipelike.beam.BlockBeamPipe;
import gregtech.common.pipelike.laser.net.WorldLaserPipeNet;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockLaserPipe extends BlockBeamPipe<LaserPipeType, LaserPipeProperties, WorldLaserPipeNet> {

    public BlockLaserPipe(@NotNull LaserPipeType pipeType) {
        super(pipeType, LaserPipeProperties.INSTANCE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(World world, BlockPos blockPos) {
        return LaserPipeRenderer.INSTANCE.getParticleTexture((TileEntityLaserPipe) world.getTileEntity(blockPos));
    }

    @Override
    public Class<LaserPipeType> getPipeTypeClass() {
        return LaserPipeType.class;
    }

    @Override
    public WorldLaserPipeNet getWorldPipeNet(World world) {
        return WorldLaserPipeNet.getWorldPipeNet(world);
    }

    @Override
    public TileEntityPipeBase<LaserPipeType, LaserPipeProperties> createNewTileEntity(boolean supportsTicking) {
        return new TileEntityLaserPipe();
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeTile<LaserPipeType, LaserPipeProperties> selfTile, EnumFacing side,
                                         @Nullable TileEntity tile) {
        return tile != null &&
                tile.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, side.getOpposite()) != null;
    }

    @Override
    @NotNull
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return LaserPipeRenderer.INSTANCE.getBlockRenderType();
    }

    /**
     * The pipe and its covers draw in {@code SOLID} and {@code CUTOUT}; its active overlay is in the emissive layer,
     * which falls back to {@code CUTOUT} (see {@link #isInEmissiveLayer}).
     */
    @Override
    public boolean canRenderInLayer(@NotNull IBlockState state, @NotNull BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.CUTOUT;
    }
}

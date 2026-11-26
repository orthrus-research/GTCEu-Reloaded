package gregtech.integration.probe;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * The block a probe looks at, as producers see it. The One Probe and HWYLA's server data give the server's world;
 * HWYLA lines that need only the block state are produced from the client's.
 */
public record ProbeContext(World world, BlockPos pos, IBlockState state, @Nullable TileEntity tileEntity,
                           boolean sneaking) {

    /** The machine in the tile entity, or null when it holds none. */
    @Nullable
    public MetaTileEntity metaTileEntity() {
        return tileEntity instanceof IGregTechTileEntity gtte ? gtte.getMetaTileEntity() : null;
    }
}

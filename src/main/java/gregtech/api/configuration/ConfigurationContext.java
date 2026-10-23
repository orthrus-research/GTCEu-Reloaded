package gregtech.api.configuration;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.BiPredicate;

public final class ConfigurationContext {

    private final World world;
    private final BiPredicate<BlockPos, EnumFacing> permission;

    public ConfigurationContext(World world, BiPredicate<BlockPos, EnumFacing> permission) {
        this.world = world;
        this.permission = permission;
    }

    public boolean canEdit(BlockPos pos, EnumFacing side) {
        return !world.isRemote && world.isBlockLoaded(pos) && permission.test(pos, side);
    }

    public void requireEditable(BlockPos pos, EnumFacing side, ConfigurationTransaction transaction) {
        ConfigurationData.require(canEdit(pos, side));
        transaction.guard(() -> canEdit(pos, side));
    }

    public TileEntity loadedTile(BlockPos pos) {
        ConfigurationData.require(world.isBlockLoaded(pos));
        return world.getTileEntity(pos);
    }

    public void guardTile(BlockPos pos, TileEntity tile, ConfigurationTransaction transaction) {
        transaction.guard(() -> world.isBlockLoaded(pos) && world.getTileEntity(pos) == tile);
    }
}

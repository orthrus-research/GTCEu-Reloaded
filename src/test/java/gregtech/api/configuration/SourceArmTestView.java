package gregtech.api.configuration;

import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverableView;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.Consumer;

final class SourceArmTestView implements CoverableView {

    final ItemStackHandler source = new ItemStackHandler(8);
    final ItemStackHandler destination = new ItemStackHandler(16);
    private final ConfigurationTestWorld world = new ConfigurationTestWorld();
    private final TileEntity endpoint = new ItemPipeTransportFixture.Inventory(destination);
    long tick;
    Cover cover;

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public BlockPos getPos() {
        return ItemPipeTransportFixture.POS;
    }

    @Override
    public TileEntity getNeighbor(EnumFacing facing) {
        return facing == EnumFacing.EAST ? endpoint : null;
    }

    @Override
    public void markDirty() {}

    @Override
    public void notifyBlockUpdate() {}

    @Override
    public void scheduleRenderUpdate() {}

    @Override
    public long getOffsetTimer() {
        return tick;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Cover getCoverAtSide(EnumFacing side) {
        return side == EnumFacing.EAST ? cover : null;
    }

    @Override
    public boolean hasAnyCover() {
        return cover != null;
    }

    @Override
    public int getInputRedstoneSignal(EnumFacing side, boolean ignoreCover) {
        return 0;
    }

    @Override
    public void writeCoverData(Cover written, int discriminator, Consumer<PacketBuffer> writer) {}

    @Override
    public ItemStack getStackForm() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) source : null;
    }
}

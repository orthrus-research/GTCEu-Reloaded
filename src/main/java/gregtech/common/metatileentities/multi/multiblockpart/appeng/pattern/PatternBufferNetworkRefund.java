package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public final class PatternBufferNetworkRefund implements PatternBufferRefund {

    private final AENetworkProxy proxy;
    private final IActionSource source;

    public PatternBufferNetworkRefund(AENetworkProxy proxy, IActionSource source) {
        this.proxy = proxy;
        this.source = source;
    }

    @Override
    public int insertItem(ItemStack stack) {
        if (!proxy.isActive() || !proxy.isPowered()) return 0;
        try {
            IAEItemStack input = AEItemStack.fromItemStack(stack);
            if (input == null) return 0;
            input.setStackSize(stack.getCount());
            IAEItemStack remainder = Platform.poweredInsert(proxy.getEnergy(), proxy.getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)), input, source);
            long left = remainder == null ? 0 : remainder.getStackSize();
            PatternBufferCodec.require(left >= 0 && left <= stack.getCount(), "Invalid AE item remainder");
            return stack.getCount() - (int) left;
        } catch (GridAccessException ignored) {
            return 0;
        }
    }

    @Override
    public int insertFluid(FluidStack stack) {
        if (!proxy.isActive() || !proxy.isPowered()) return 0;
        try {
            IAEFluidStack input = AEFluidStack.fromFluidStack(stack);
            if (input == null) return 0;
            IAEFluidStack remainder = Platform.poweredInsert(proxy.getEnergy(), proxy.getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)), input, source);
            long left = remainder == null ? 0 : remainder.getStackSize();
            PatternBufferCodec.require(left >= 0 && left <= stack.amount, "Invalid AE fluid remainder");
            return stack.amount - (int) left;
        } catch (GridAccessException ignored) {
            return 0;
        }
    }
}

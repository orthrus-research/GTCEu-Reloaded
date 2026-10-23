package gregtech.common.covers.filter;

import gregtech.api.util.FluidStackHashStrategy;
import gregtech.api.util.ItemStackHashStrategy;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;

/** Explicit advanced-voiding behavior: retain the global amount of each accepted exact item/fluid type. */
public final class CompositeFilterOverflow {

    private CompositeFilterOverflow() {}

    public static void voidItems(IItemHandler handler, CompositeFilter filter, int keep) {
        Object2LongMap<ItemStack> totals = new Object2LongOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                totals.put(stack.copy(), totals.getLong(stack) + stack.getCount());
            }
        }
        for (Object2LongMap.Entry<ItemStack> entry : totals.object2LongEntrySet()) {
            long excess = entry.getLongValue() - keep;
            for (int i = 0; i < handler.getSlots() && excess > 0; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (ItemStack.areItemsEqual(stack, entry.getKey()) &&
                        ItemStack.areItemStackTagsEqual(stack, entry.getKey())) {
                    excess -= handler.extractItem(i, (int) Math.min(excess, Integer.MAX_VALUE), false).getCount();
                }
            }
        }
    }

    public static void voidFluids(IFluidHandler handler, CompositeFilter filter, int keep) {
        Object2LongMap<FluidStack> totals = new Object2LongOpenCustomHashMap<>(
                FluidStackHashStrategy.comparingAllButAmount());
        for (IFluidTankProperties tank : handler.getTankProperties()) {
            FluidStack fluid = tank.getContents();
            if (fluid != null && fluid.amount > 0 && tank.canDrain() && filter.test(fluid)) {
                totals.put(fluid.copy(), totals.getLong(fluid) + fluid.amount);
            }
        }
        for (Object2LongMap.Entry<FluidStack> entry : totals.object2LongEntrySet()) {
            long excess = entry.getLongValue() - keep;
            if (excess > 0) {
                FluidStack request = entry.getKey().copy();
                request.amount = (int) Math.min(excess, Integer.MAX_VALUE);
                // One request per exact type per normal voiding tick, even if the handler drains only part.
                handler.drain(request, true);
            }
        }
    }
}

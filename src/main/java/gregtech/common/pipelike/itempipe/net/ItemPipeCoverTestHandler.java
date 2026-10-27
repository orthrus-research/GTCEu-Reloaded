package gregtech.common.pipelike.itempipe.net;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** One virtual packet for cover admission; exact packets may contain more than one ordinary item stack. */
final class ItemPipeCoverTestHandler extends ItemStackHandler {

    ItemPipeCoverTestHandler() {
        super(1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        validateSlotIndex(slot);
        ItemStack present = getStackInSlot(slot);
        if (amount <= 0 || present.isEmpty()) return ItemStack.EMPTY;
        int count = Math.min(amount, present.getCount());
        ItemStack extracted = present.copy();
        extracted.setCount(count);
        if (!simulate) {
            ItemStack remaining = present.copy();
            remaining.shrink(count);
            setStackInSlot(slot, remaining);
        }
        return extracted;
    }
}

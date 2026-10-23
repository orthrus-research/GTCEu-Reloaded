package gregtech.api.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.NotNull;

public class OverlayedItemHandler {

    private final OverlayedItemHandlerSlot[] originalSlots;
    private final OverlayedItemHandlerSlot[] slots;
    private final IItemHandler overlayedHandler;

    public OverlayedItemHandler(@NotNull IItemHandler toOverlay) {
        this.slots = new OverlayedItemHandlerSlot[toOverlay.getSlots()];
        this.originalSlots = new OverlayedItemHandlerSlot[toOverlay.getSlots()];
        this.overlayedHandler = toOverlay;
    }

    /**
     * Resets the {slots} array to the state when the handler was
     * first mirrored
     */
    public void reset() {
        for (int i = 0; i < this.originalSlots.length; i++) {
            if (this.originalSlots[i] != null) {
                this.slots[i] = this.originalSlots[i].copy();
            }
        }
    }

    public int getSlots() {
        return overlayedHandler.getSlots();
    }

    /**
     * Populates the {@code originalSlots} and {@code slots}arrays with the current state of the inventory.
     *
     * @param slot the slot to populate
     */

    private void initSlot(int slot) {
        if (this.originalSlots[slot] == null) {
            ItemStack stackToMirror = overlayedHandler.getStackInSlot(slot);
            int slotLimit = overlayedHandler.getSlotLimit(slot);
            this.originalSlots[slot] = new OverlayedItemHandlerSlot(stackToMirror, slotLimit);
            this.slots[slot] = new OverlayedItemHandlerSlot(stackToMirror, slotLimit);
        }
    }

    public int insertStackedItemStack(@NotNull ItemStack stack, int amountToInsert) {
        if (stack.isEmpty() || amountToInsert <= 0) return 0;
        // Match GTTransferUtils.insertItem: top up matching stacks before using any empty slot.
        for (int i = 0; i < this.slots.length; i++) {
            initSlot(i);
            OverlayedItemHandlerSlot slot = this.slots[i];
            if (!slot.getItemStack().isEmpty() &&
                    ItemHandlerHelper.canItemStacksStack(stack, slot.getItemStack()) &&
                    overlayedHandler.isItemValid(i, stack)) {
                int canInsertUpTo = slot.getSlotLimit() - slot.getCount();
                if (canInsertUpTo > 0) {
                    int insertedAmount = Math.min(canInsertUpTo, amountToInsert);
                    slot.setCount(slot.getCount() + insertedAmount);
                    amountToInsert -= insertedAmount;
                }
            }
            if (amountToInsert == 0) return 0;
        }
        for (int i = 0; i < this.slots.length; i++) {
            OverlayedItemHandlerSlot slot = this.slots[i];
            if (slot.getItemStack().isEmpty() && overlayedHandler.isItemValid(i, stack)) {
                int canInsertUpTo = Math.min(stack.getMaxStackSize(), slot.getSlotLimit());
                if (canInsertUpTo > 0) {
                    int insertedAmount = Math.min(canInsertUpTo, amountToInsert);
                    slot.setItemStack(stack.copy());
                    slot.setCount(insertedAmount);
                    amountToInsert -= insertedAmount;
                }
                if (amountToInsert == 0) return 0;
            }
        }
        return amountToInsert;
    }

    private static class OverlayedItemHandlerSlot {

        private ItemStack itemStack = ItemStack.EMPTY;
        private int count = 0;
        private int slotLimit;

        protected OverlayedItemHandlerSlot(@NotNull ItemStack stackToMirror, int slotLimit) {
            if (!stackToMirror.isEmpty()) {
                this.itemStack = stackToMirror.copy();
                this.count = stackToMirror.getCount();
                this.slotLimit = Math.min(itemStack.getMaxStackSize(), slotLimit);
            } else {
                this.slotLimit = slotLimit;
            }
        }

        protected OverlayedItemHandlerSlot(@NotNull ItemStack itemStack, int slotLimit, int count) {
            this.itemStack = itemStack;
            this.count = count;
            this.slotLimit = slotLimit;
        }

        public int getSlotLimit() {
            return slotLimit;
        }

        public int getCount() {
            return count;
        }

        /**
         * Storage of this ItemStack elsewhere will require copying it
         * 
         * @return the stored ItemStack
         */
        @NotNull
        public ItemStack getItemStack() {
            return this.itemStack;
        }

        public void setItemStack(@NotNull ItemStack itemStack) {
            if (!ItemStackHashStrategy.comparingAllButCount().equals(this.itemStack, itemStack)) {
                this.itemStack = itemStack;
                this.slotLimit = Math.min(itemStack.getMaxStackSize(), slotLimit);
            }
        }

        public void setCount(int count) {
            this.count = count;
        }

        @NotNull
        OverlayedItemHandlerSlot copy() {
            return new OverlayedItemHandlerSlot(this.itemStack, this.slotLimit, this.count);
        }
    }
}

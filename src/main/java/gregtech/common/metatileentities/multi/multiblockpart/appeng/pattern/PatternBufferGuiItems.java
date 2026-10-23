package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

/** Native container slots mutate their backing stacks directly; only this UI adapter exposes that contract. */
final class PatternBufferGuiItems implements IItemHandlerModifiable {

    private final PatternBufferItems items;
    private final ItemStack[] observed;

    PatternBufferGuiItems(PatternBufferItems items) {
        this.items = items;
        this.observed = new ItemStack[items.getSlots()];
        for (int i = 0; i < observed.length; i++) observe(i);
    }

    @Override
    public int getSlots() {
        return items.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return items.getGuiStack(slot);
    }

    @Override
    public int getSlotLimit(int slot) {
        return items.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return items.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        ItemStack before = items.getStackInSlot(slot);
        items.setStackInSlot(slot, stack);
        // A container may pass the same backing stack after mutating it, so the store's equality guard sees no edit.
        if (ItemStack.areItemStacksEqual(before, items.getStackInSlot(slot))) onSlotChanged(slot);
        else observe(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        ItemStack result = items.insertItem(slot, stack, simulate);
        if (!simulate) observe(slot);
        return result;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack result = items.extractItem(slot, amount, simulate);
        if (!simulate) observe(slot);
        return result;
    }

    void onSlotChanged(int slot) {
        if (!ItemStack.areItemStacksEqual(observed[slot], items.getStackInSlot(slot))) {
            items.guiChanged(slot);
            observe(slot);
        }
    }

    private void observe(int slot) {
        this.observed[slot] = items.getStackInSlot(slot);
    }
}

package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/** Filters insertion without discarding existing contents or restricting extraction. */
public final class FilteredNotifiableItemStackHandler extends NotifiableItemStackHandler {

    private final Predicate<ItemStack> filter;

    public FilteredNotifiableItemStackHandler(MetaTileEntity owner, int slots, MetaTileEntity controller,
                                              Predicate<ItemStack> filter) {
        super(owner, slots, controller, true);
        this.filter = filter;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return filter.test(stack);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        // Forge 1.12's insertItem does not invoke isItemValid.
        return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
    }
}

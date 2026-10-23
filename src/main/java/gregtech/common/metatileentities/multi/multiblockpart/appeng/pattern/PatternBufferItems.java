package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiPredicate;

/** Private stores allow extraction only; shared/pattern stores validate every real and simulated insertion. */
public final class PatternBufferItems extends ItemStackHandler {

    private final Runnable changed;
    private final BiPredicate<Integer, ItemStack> allowed;
    private final int limit;
    private final boolean privateInputs;

    public PatternBufferItems(int slots, int limit, boolean privateInputs, Runnable changed,
                              BiPredicate<Integer, ItemStack> allowed) {
        super(slots);
        this.limit = limit;
        this.privateInputs = privateInputs;
        this.changed = changed;
        this.allowed = allowed;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return super.getStackInSlot(slot).copy();
    }

    ItemStack getGuiStack(int slot) {
        if (privateInputs) throw new IllegalStateException("Private batch is not a native GUI inventory");
        return super.getStackInSlot(slot);
    }

    void guiChanged(int slot) {
        onContentsChanged(slot);
    }

    @Override
    public int getSlotLimit(int slot) {
        return limit;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return !privateInputs && allowed.test(slot, stack) &&
                PatternBufferCodec.fits(PatternBufferCodec.writeItem(stack), PatternBufferCodec.MAX_STACK_BYTES);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) return stack;
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (privateInputs || (!stack.isEmpty() && (!isItemValid(slot, stack) || stack.getCount() > limit)) ||
                !allowed.test(slot, stack))
            return;
        restore(slot, stack);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!allowed.test(slot, ItemStack.EMPTY)) return ItemStack.EMPTY;
        if (privateInputs) {
            validateSlotIndex(slot);
            ItemStack existing = stacks.get(slot);
            if (amount <= 0 || existing.isEmpty()) return ItemStack.EMPTY;
            int extracted = Math.min(amount, existing.getCount());
            ItemStack result = existing.copy();
            result.setCount(extracted);
            if (!simulate) {
                ItemStack remaining = existing.copy();
                remaining.shrink(extracted);
                restore(slot, remaining);
            }
            return result;
        }
        return super.extractItem(slot, amount, simulate);
    }

    /** Only validated codec/staging/refund code uses this; never an automation capability. */
    void restore(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (ItemStack.areItemStacksEqual(stacks.get(slot), stack)) return;
        this.stacks.set(slot, stack.copy());
        onContentsChanged(slot);
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (changed != null) changed.run();
    }
}

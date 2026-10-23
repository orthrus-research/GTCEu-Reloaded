package gregtech.common.covers.filter;

import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

/** Physical leaf filters. The fixed list is never resized from serialized data. */
public final class CompositeFilterInventory extends ItemStackHandler {

    public static final int SIZE = 9;
    static final String ITEMS_KEY = "CompositeItems";
    private final ItemStack parent;
    private final IFilter.FilterType type;
    private final Runnable changed;
    private final boolean validShape;
    private NBTBase storedList;
    private BooleanSupplier owner = () -> false;
    private boolean client;

    CompositeFilterInventory(ItemStack parent, IFilter.FilterType type, Runnable changed) {
        super(SIZE);
        this.parent = parent;
        this.type = type;
        this.changed = changed;
        this.storedList = parent.hasTagCompound() ? parent.getTagCompound().getTag(ITEMS_KEY) : null;
        boolean shape = storedList == null || storedList instanceof NBTTagList list &&
                list.tagCount() == SIZE && list.getTagType() == NBT.TAG_COMPOUND;
        if (shape && storedList instanceof NBTTagList list) {
            for (int i = 0; i < SIZE; i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                ItemStack child = new ItemStack(entry);
                if (!entry.isEmpty() && child.isEmpty()) shape = false;
                this.stacks.set(i, child);
            }
        }
        this.validShape = shape;
    }

    public void setOwner(BooleanSupplier owner) {
        this.owner = owner;
    }

    void setClient(boolean client) {
        this.client = client;
    }

    public boolean isValidShape() {
        return validShape && isCurrent();
    }

    public boolean canEdit() {
        return isValidShape() && owner.getAsBoolean();
    }

    private boolean isCurrent() {
        return (parent.hasTagCompound() ? parent.getTagCompound().getTag(ITEMS_KEY) : null) == storedList;
    }

    public boolean isLeaf(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (type == IFilter.FilterType.FLUID) {
            return MetaItems.FLUID_FILTER != null && MetaItems.FLUID_FILTER.isItemEqual(stack);
        }
        return MetaItems.ITEM_FILTER != null && MetaItems.ITEM_FILTER.isItemEqual(stack) ||
                MetaItems.ORE_DICTIONARY_FILTER != null && MetaItems.ORE_DICTIONARY_FILTER.isItemEqual(stack) ||
                MetaItems.SMART_FILTER != null && MetaItems.SMART_FILTER.isItemEqual(stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return canEdit() && isLeaf(stack);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        // Forge 1.12's ItemStackHandler does not call isItemValid from insertItem.
        return canEdit() && isLeaf(stack) ? super.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return canEdit() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (client) {
            validateSlotIndex(slot);
            this.stacks.set(slot, stack);
            changed.run();
            return;
        }
        if (!canEdit()) return;
        // Shift-click may return a smaller remainder of an invalid retained stack. Preserve that real remainder.
        ItemStack existing = getStackInSlot(slot);
        boolean removalRemainder = !existing.isEmpty() && stack.getCount() <= existing.getCount() &&
                ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack);
        if (!stack.isEmpty() && (!isLeaf(stack) || stack.getCount() > 1) && !removalRemainder) return;
        super.setStackInSlot(slot, stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : stacks) {
            list.appendTag(stack.isEmpty() ? new NBTTagCompound() : stack.writeToNBT(new NBTTagCompound()));
        }
        NBTTagCompound tag = parent.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            parent.setTagCompound(tag);
        }
        tag.setTag(ITEMS_KEY, list);
        this.storedList = list;
        changed.run();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        throw new UnsupportedOperationException("Composite contents belong to their physical parent item");
    }

    @Override
    public void setSize(int size) {
        throw new UnsupportedOperationException("Composite inventories always have nine physical slots");
    }
}

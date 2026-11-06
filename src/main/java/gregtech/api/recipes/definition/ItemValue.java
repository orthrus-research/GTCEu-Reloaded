package gregtech.api.recipes.definition;

import gregtech.api.GTValues;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/** Owns a stack snapshot using Forge's copy/serialized-capability contract; callers only receive copies. */
public final class ItemValue {

    private final ItemStack prototype;
    private final NBTTagCompound serialized;

    private ItemValue(ItemStack stack) {
        this.prototype = Objects.requireNonNull(stack, "stack").copy();
        this.serialized = this.prototype.serializeNBT();
    }

    public static ItemValue of(ItemStack stack) {
        return new ItemValue(stack);
    }

    public ItemStack stack() {
        return prototype.copy();
    }

    public boolean isEmpty() {
        return prototype.isEmpty();
    }

    boolean matchesCapabilities(ItemStack candidate) {
        return prototype.getItem() == candidate.getItem() &&
                (prototype.getMetadata() == GTValues.W || prototype.getMetadata() == candidate.getMetadata()) &&
                prototype.areCapsCompatible(candidate);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemValue value && prototype.getItem() == value.prototype.getItem() &&
                prototype.getCount() == value.prototype.getCount() && serialized.equals(value.serialized);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prototype.getItem(), prototype.getCount(), serialized);
    }
}

package gregtech.common.covers.filter;

import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

/** Validate present leaf fields before legacy readers can coerce malformed data into empty blacklists. */
final class CompositeFilterValidation {

    private CompositeFilterValidation() {}

    static boolean isValid(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return true;
        try {
            if (!optional(tag, "IsBlacklist", NBT.TAG_ANY_NUMERIC)) return false;
            if (MetaItems.FLUID_FILTER.isItemEqual(stack)) {
                return optional(tag, "Capacity", NBT.TAG_ANY_NUMERIC) &&
                        (!tag.hasKey("Capacity") || tag.getInteger("Capacity") >= 0) && slots(tag, 9, true);
            }
            if (!optional(tag, "IgnoreNBT", NBT.TAG_ANY_NUMERIC) ||
                    !optional(tag, "IgnoreDamage", NBT.TAG_ANY_NUMERIC))
                return false;
            if (MetaItems.ITEM_FILTER.isItemEqual(stack)) return slots(tag, 9, false);
            if (!slots(tag, 0, false)) return false;
            if (MetaItems.SMART_FILTER.isItemEqual(stack)) {
                return optional(tag, "FilterMode", NBT.TAG_ANY_NUMERIC) &&
                        (!tag.hasKey("FilterMode") || tag.getInteger("FilterMode") >= 0 &&
                                tag.getInteger("FilterMode") < SmartItemFilter.SmartFilteringMode.VALUES.length);
            }
            return optional(tag, "OreDictionaryFilter", NBT.TAG_STRING) &&
                    optional(tag, "caseSensitive", NBT.TAG_ANY_NUMERIC) &&
                    optional(tag, "matchAll", NBT.TAG_ANY_NUMERIC);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean optional(NBTTagCompound tag, String key, int type) {
        return !tag.hasKey(key) || tag.hasKey(key, type);
    }

    private static boolean slots(NBTTagCompound tag, int size, boolean fluid) {
        if (!tag.hasKey("FilterSlots")) return true;
        if (!(tag.getTag("FilterSlots") instanceof NBTTagList list) || list.tagCount() != size ||
                size > 0 && list.getTagType() != NBT.TAG_COMPOUND)
            return false;
        for (int i = 0; i < size; i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.isEmpty()) continue;
            if (fluid ? !fluid(entry) : !item(entry)) return false;
        }
        return true;
    }

    private static boolean item(NBTTagCompound entry) {
        return entry.hasKey("id", NBT.TAG_STRING) && entry.hasKey("Count", NBT.TAG_ANY_NUMERIC) &&
                optional(entry, "Damage", NBT.TAG_ANY_NUMERIC) && optional(entry, "tag", NBT.TAG_COMPOUND) &&
                optional(entry, "ForgeCaps", NBT.TAG_COMPOUND) && !new ItemStack(entry.copy()).isEmpty();
    }

    private static boolean fluid(NBTTagCompound entry) {
        if (!entry.hasKey("Fluid", NBT.TAG_COMPOUND)) return false;
        NBTTagCompound fluid = entry.getCompoundTag("Fluid");
        return fluid.hasKey("FluidName", NBT.TAG_STRING) && fluid.hasKey("Amount", NBT.TAG_ANY_NUMERIC) &&
                fluid.getInteger("Amount") >= 0 && optional(fluid, "Tag", NBT.TAG_COMPOUND) &&
                FluidStack.loadFluidStackFromNBT(fluid) != null;
    }
}

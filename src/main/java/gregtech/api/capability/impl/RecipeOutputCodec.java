package gregtech.api.capability.impl;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The saved form of the outputs a running recipe will insert. An item is saved as one item of its kind with its full
 * count under {@value #ITEM_COUNT}, so counts above the item's stack limit survive; an older save without that key
 * keeps the item's own count. A fluid that is no longer registered is dropped when read.
 */
final class RecipeOutputCodec {

    static final String ITEM_COUNT = "RecipeOutputCount";

    private RecipeOutputCodec() {}

    static NBTTagList writeItems(List<ItemStack> outputs) {
        NBTTagList list = new NBTTagList();
        for (ItemStack output : outputs) {
            if (output.isEmpty()) continue;
            ItemStack identity = output.copy();
            identity.setCount(1);
            NBTTagCompound tag = identity.writeToNBT(new NBTTagCompound());
            tag.setInteger(ITEM_COUNT, output.getCount());
            list.appendTag(tag);
        }
        return list;
    }

    /** @return a new, modifiable list; entries with a count that is not a positive integer are skipped */
    static List<ItemStack> readItems(NBTTagList list) {
        List<ItemStack> outputs = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.hasKey(ITEM_COUNT) &&
                    (!tag.hasKey(ITEM_COUNT, Constants.NBT.TAG_INT) || tag.getInteger(ITEM_COUNT) <= 0))
                continue;
            ItemStack output = new ItemStack(tag);
            if (!output.isEmpty() && tag.hasKey(ITEM_COUNT, Constants.NBT.TAG_INT)) {
                output.setCount(tag.getInteger(ITEM_COUNT));
            }
            outputs.add(output);
        }
        return outputs;
    }

    static NBTTagList writeFluids(List<FluidStack> outputs) {
        NBTTagList list = new NBTTagList();
        for (FluidStack output : outputs) {
            list.appendTag(output.writeToNBT(new NBTTagCompound()));
        }
        return list;
    }

    /** @return a new, modifiable list */
    static List<FluidStack> readFluids(NBTTagList list) {
        List<FluidStack> outputs = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            // null for a fluid that is no longer registered
            FluidStack output = FluidStack.loadFluidStackFromNBT(list.getCompoundTagAt(i));
            if (output != null) {
                outputs.add(output);
            }
        }
        return outputs;
    }
}

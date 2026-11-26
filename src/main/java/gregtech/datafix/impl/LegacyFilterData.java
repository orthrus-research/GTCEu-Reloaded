package gregtech.datafix.impl;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Rewrites cover filters saved before GTCEu's filter rework (upstream #2345), which no 2.8 release has. Until then a
 * cover kept its filter's settings in its own NBT: a conveyor, robotic arm, pump or regulator in its {@code Filter}
 * container beside the filter item, a filter cover at the top of its tag. Filters keep their settings in their item's
 * tag now. Each method changes a cover's tag in place and leaves a tag already in the current format alone.
 */
public final class LegacyFilterData {

    private static final String META_ITEM = "gregtech:meta_item_1";
    private static final int FLUID_FILTER = 290;
    private static final int ITEM_FILTER = 291;
    private static final int ORE_DICTIONARY_FILTER = 292;
    private static final int SMART_FILTER = 293;
    /** The simple item and fluid filters had nine slots then, as they have now. */
    private static final int SLOT_COUNT = 9;
    /** The filter item each filter cover drops, which held that cover's filter. */
    private static final Map<String, Integer> FILTER_COVERS = Map.of(
            "gregtech:fluid_filter", FLUID_FILTER,
            "gregtech:item_filter", ITEM_FILTER,
            "gregtech:ore_dictionary_filter", ORE_DICTIONARY_FILTER,
            "gregtech:smart_filter", SMART_FILTER);

    private static final String FILTER = "Filter";
    private static final String IS_BLACKLIST = "IsBlacklist";
    private static final String FILTER_INVENTORY = "FilterInventory";
    private static final String TRANSFER_STACK_SIZE = "TransferStackSize";
    private static final String FILTER_SLOTS_KEY = "FilterSlots";
    private static final String ITEMS = "Items";
    private static final String SLOT = "Slot";
    private static final String TAG = "tag";

    private LegacyFilterData() {}

    /**
     * Converts a cover's filter: the container of a conveyor or pump and their subclasses, or the filter of an item,
     * ore dictionary, smart or fluid filter cover.
     */
    public static void fixCover(@NotNull ResourceLocation coverId, @NotNull NBTTagCompound cover) {
        if (cover.getCompoundTag(FILTER).hasKey(IS_BLACKLIST)) {
            cover.setTag(FILTER, container(cover.getCompoundTag(FILTER)));
        } else if (cover.hasKey(IS_BLACKLIST)) {
            Integer filterItem = FILTER_COVERS.get(coverId.toString());
            if (filterItem != null) {
                cover.setTag(FILTER, filterCover(filterItem, cover.getBoolean(IS_BLACKLIST),
                        cover.getCompoundTag(FILTER)));
                cover.removeTag(IS_BLACKLIST);
            }
        }
    }

    /**
     * Before GTCEu 2.2.0 (upstream #466), a fluid regulator saved one {@code TransferAmount} for all of its filter's
     * slots; since then it wrote an empty {@code filterv2} marker to say it did not. The amount becomes each fluid's
     * amount in its simple fluid filter. Run it after {@link #fixCover}.
     */
    public static void fixRegulatorAmount(@NotNull NBTTagCompound cover) {
        if (cover.hasKey("filterv2") || !cover.hasKey("TransferAmount", Constants.NBT.TAG_ANY_NUMERIC)) return;
        int amount = cover.getInteger("TransferAmount");
        cover.removeTag("TransferAmount");
        NBTTagCompound filter = filterStack(cover.getCompoundTag(FILTER));
        if (filter == null || filter.getShort("Damage") != FLUID_FILTER) return;
        NBTTagList tanks = filter.getCompoundTag(TAG).getTagList(FILTER_SLOTS_KEY, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tanks.tagCount(); i++) {
            NBTTagCompound tank = tanks.getCompoundTagAt(i);
            if (!tank.hasKey("Fluid")) continue;
            if (amount <= 0) {
                tank.removeTag("Fluid");
            } else {
                tank.getCompoundTag("Fluid").setInteger("Amount", amount);
            }
        }
    }

    /** The legacy container held the filter item, its blacklist flag, the filter's settings and its sizes. */
    private static @NotNull NBTTagCompound container(@NotNull NBTTagCompound legacy) {
        NBTTagCompound container = new NBTTagCompound();
        if (legacy.hasKey(FILTER_INVENTORY, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound inventory = legacy.getCompoundTag(FILTER_INVENTORY).copy();
            container.setTag(FILTER_INVENTORY, inventory);
            NBTTagCompound filter = filterStack(container);
            if (filter != null) {
                writeSettings(filter, legacy.getBoolean(IS_BLACKLIST), legacy.getCompoundTag(FILTER));
            }
        }
        if (legacy.hasKey(TRANSFER_STACK_SIZE, Constants.NBT.TAG_ANY_NUMERIC)) {
            container.setInteger(TRANSFER_STACK_SIZE, legacy.getInteger(TRANSFER_STACK_SIZE));
        }
        return container;
    }

    /** A legacy filter cover held no item: its filter was the one it drops. */
    private static @NotNull NBTTagCompound filterCover(int filterItem, boolean blacklist,
                                                       @NotNull NBTTagCompound settings) {
        NBTTagCompound filter = new NBTTagCompound();
        filter.setInteger(SLOT, 0);
        filter.setString("id", META_ITEM);
        filter.setByte("Count", (byte) 1);
        filter.setShort("Damage", (short) filterItem);
        writeSettings(filter, blacklist, settings);

        NBTTagList items = new NBTTagList();
        items.appendTag(filter);
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setTag(ITEMS, items);
        inventory.setInteger("Size", 1);
        NBTTagCompound container = new NBTTagCompound();
        container.setTag(FILTER_INVENTORY, inventory);
        return container;
    }

    /** @return the filter item in slot 0 of a container's filter inventory, or null when the slot is empty */
    private static @Nullable NBTTagCompound filterStack(@NotNull NBTTagCompound container) {
        NBTTagList items = container.getCompoundTag(FILTER_INVENTORY).getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound item = items.getCompoundTagAt(i);
            if (item.getInteger(SLOT) == 0) return META_ITEM.equals(item.getString("id")) ? item : null;
        }
        return null;
    }

    /** Writes a legacy filter's settings into the tag of its item, which is one of GTCEu's four filters then. */
    private static void writeSettings(@NotNull NBTTagCompound filter, boolean blacklist,
                                      @NotNull NBTTagCompound settings) {
        NBTTagCompound tag = filter.getCompoundTag(TAG);
        switch (filter.getShort("Damage")) {
            case ITEM_FILTER -> simpleItemFilter(tag, settings);
            case ORE_DICTIONARY_FILTER -> oreDictionaryFilter(tag, settings);
            case SMART_FILTER -> smartFilter(tag, settings);
            case FLUID_FILTER -> fluidFilter(tag, settings);
            default -> {
                return;
            }
        }
        if (blacklist) tag.setBoolean(IS_BLACKLIST, true);
        if (!tag.isEmpty()) filter.setTag(TAG, tag);
    }

    /**
     * The slots were an item handler whose larger counts were kept in {@code BigStackSize}; the filter keeps each
     * slot's stack with an int count now. Legacy filters saved whether they ignored damage and NBT, and ignoring is
     * the default now, so only a filter that respected them keeps a key, which says that it respects them.
     */
    private static void simpleItemFilter(@NotNull NBTTagCompound tag, @NotNull NBTTagCompound settings) {
        if (settings.hasKey("ItemFilter", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound legacySlots = settings.getCompoundTag("ItemFilter");
            NBTTagCompound counts = legacySlots.getCompoundTag("BigStackSize");
            NBTTagList items = legacySlots.getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
            NBTTagList slots = emptySlots();
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound item = items.getCompoundTagAt(i).copy();
                int slot = item.getInteger(SLOT);
                if (slot < 0 || slot >= SLOT_COUNT) continue;
                String countKey = String.valueOf(slot);
                int count = counts.hasKey(countKey, Constants.NBT.TAG_ANY_NUMERIC) ? counts.getInteger(countKey) :
                        item.getInteger("Count");
                item.removeTag(SLOT);
                item.setInteger("Count", count);
                slots.set(slot, item);
            }
            tag.setTag(FILTER_SLOTS_KEY, slots);
        }
        for (String key : new String[] { "IgnoreDamage", "IgnoreNBT" }) {
            if (settings.hasKey(key, Constants.NBT.TAG_ANY_NUMERIC) && !settings.getBoolean(key)) {
                tag.setBoolean(key, true);
            }
        }
    }

    /**
     * A legacy ore dictionary filter saved {@code caseSensitive} and {@code matchAll} only when they were on. The
     * filter now saves them only when they are off.
     */
    private static void oreDictionaryFilter(@NotNull NBTTagCompound tag, @NotNull NBTTagCompound settings) {
        String expression = settings.getString("OreDictionaryFilter");
        if (!expression.isEmpty()) tag.setString("OreDictionaryFilter", expression);
        for (String key : new String[] { "caseSensitive", "matchAll" }) {
            if (!settings.getBoolean(key)) tag.setBoolean(key, false);
        }
    }

    /** The mode keeps its ordinal; the filter reads one that names no mode as its default, as before. */
    private static void smartFilter(@NotNull NBTTagCompound tag, @NotNull NBTTagCompound settings) {
        tag.setInteger("FilterMode", settings.getInteger("FilterMode"));
    }

    /** Fluids the game no longer has are left out, as the filter would not load them. */
    private static void fluidFilter(@NotNull NBTTagCompound tag, @NotNull NBTTagCompound settings) {
        NBTTagList fluids = settings.getTagList("FluidFilter", Constants.NBT.TAG_COMPOUND);
        if (fluids.isEmpty()) return;
        NBTTagList slots = emptySlots();
        for (int i = 0; i < fluids.tagCount(); i++) {
            NBTTagCompound saved = fluids.getCompoundTagAt(i);
            int slot = saved.getInteger(SLOT);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(saved);
            if (fluid == null || slot < 0 || slot >= SLOT_COUNT) continue;
            NBTTagCompound tank = new NBTTagCompound();
            tank.setTag("Fluid", fluid.writeToNBT(new NBTTagCompound()));
            slots.set(slot, tank);
        }
        tag.setTag(FILTER_SLOTS_KEY, slots);
    }

    private static @NotNull NBTTagList emptySlots() {
        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.appendTag(new NBTTagCompound());
        }
        return slots;
    }
}

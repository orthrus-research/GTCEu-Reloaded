package gregtech.datafix.migration.lib;

import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverFluidRegulator;
import gregtech.common.covers.CoverItemFilter;
import gregtech.common.covers.CoverPump;
import gregtech.common.covers.ItemFilterMode;
import gregtech.common.covers.filter.CompositeFilterTestSupport;
import gregtech.common.covers.filter.OreDictionaryItemFilter;
import gregtech.common.covers.filter.SimpleFluidFilter;
import gregtech.common.covers.filter.SimpleItemFilter;
import gregtech.common.covers.filter.SmartItemFilter;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.electric.MetaTileEntityHull;
import gregtech.datafix.impl.V6LegacyDataFixer;
import gregtech.datafix.migration.impl.MigrateCoverData;

import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

/** Feeds covers saved before filters kept their settings on their item through the V6 cover fix, then loads them. */
class LegacyCoverDataMigrationTest {

    private static CompositeFilterTestSupport support;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        support = new CompositeFilterTestSupport();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        support.close();
    }

    @Test
    void chunkWalkReachesMachineAndPipeCovers() {
        NBTTagCompound conveyor = legacyConveyor();
        NBTTagCompound pump = legacyPump();
        NBTTagCompound chunk = chunk(machineTile(conveyor), pipeTile(pump));
        new MigrateCoverData(6, V6LegacyDataFixer::fixCover).fixTagCompound(chunk);

        NBTTagList tiles = chunk.getCompoundTag("Level").getTagList("TileEntities", Constants.NBT.TAG_COMPOUND);
        NBTTagCompound fixedConveyor = tiles.getCompoundTagAt(0).getCompoundTag("MetaTileEntity")
                .getTagList("Covers", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
        NBTTagCompound fixedPump = tiles.getCompoundTagAt(1).getTagList("Covers", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0);
        assertEquals(Set.of("FilterInventory", "TransferStackSize"),
                fixedConveyor.getCompoundTag("Filter").getKeySet());
        assertEquals(Set.of("FilterInventory"), fixedPump.getCompoundTag("Filter").getKeySet());
    }

    @Test
    void conveyorKeepsItsSimpleItemFilterSlotsCountsAndFlags() {
        NBTTagCompound saved = legacyConveyor();
        fix(saved);
        NBTTagCompound container = saved.getCompoundTag("Filter");
        assertEquals(5, container.getInteger("TransferStackSize"));
        NBTTagList slots = filterTag(container).getTagList("FilterSlots", Constants.NBT.TAG_COMPOUND);
        assertEquals(9, slots.tagCount());
        // the larger count came from BigStackSize; counts are ints now
        assertEquals(100, slots.getCompoundTagAt(0).getInteger("Count"));
        assertEquals(Constants.NBT.TAG_INT, slots.getCompoundTagAt(4).getTag("Count").getId());
        assertEquals(2, slots.getCompoundTagAt(4).getInteger("Count"));
        assertFalse(slots.getCompoundTagAt(0).hasKey("Slot"));

        CoverConveyor conveyor = new CoverConveyor(null, holder(), EnumFacing.UP, 1, 64);
        conveyor.readFromNBT(saved);
        assertEquals(7, conveyor.getTransferRate());
        assertEquals(CoverConveyor.ConveyorMode.IMPORT, conveyor.getConveyorMode());
        SimpleItemFilter filter = (SimpleItemFilter) conveyor.getItemFilterContainer().getFilter();
        assertNotNull(filter);
        assertFalse(filter.isBlacklistFilter());
        assertEquals(Items.IRON_INGOT, filter.getFilterReader().getStackInSlot(0).getItem());
        assertEquals(Items.GOLD_INGOT, filter.getFilterReader().getStackInSlot(4).getItem());
        assertFalse(filter.getFilterReader().isIgnoreDamage(), "the legacy filter respected damage");
        assertTrue(filter.getFilterReader().isIgnoreNBT());
    }

    @Test
    void oreDictionaryFilterCoverKeepsItsExpressionAndMatching() {
        for (boolean flags : new boolean[] { false, true }) {
            NBTTagCompound saved = cover("gregtech:ore_dictionary_filter");
            saved.setInteger("FilterMode", ItemFilterMode.FILTER_BOTH.ordinal());
            saved.setBoolean("IsBlacklist", true);
            NBTTagCompound settings = new NBTTagCompound();
            settings.setString("OreDictionaryFilter", "ingot*");
            // legacy filters saved these only when they were on
            if (flags) {
                settings.setBoolean("caseSensitive", true);
                settings.setBoolean("matchAll", true);
            }
            saved.setTag("Filter", settings);
            fix(saved);
            assertFalse(saved.hasKey("IsBlacklist"));

            CoverItemFilter cover = new CoverItemFilter(null, holder(), EnumFacing.UP, "legacy", null);
            cover.readFromNBT(saved);
            assertEquals(ItemFilterMode.FILTER_BOTH, cover.getFilterMode());
            OreDictionaryItemFilter filter = (OreDictionaryItemFilter) cover.getFilterContainer().getFilter();
            assertNotNull(filter);
            assertTrue(filter.isBlacklistFilter());
            assertEquals("ingot*", filter.getExpression());
            assertEquals(flags, filter.getFilterReader().isCaseSensitive());
            assertEquals(flags, filter.getFilterReader().shouldMatchAll());
        }
    }

    @Test
    void smartFilterCoverKeepsItsModeAndFallsBackForAnUnknownOne() {
        for (int mode : new int[] { 2, 99 }) {
            NBTTagCompound saved = cover("gregtech:smart_filter");
            saved.setInteger("FilterMode", 0);
            saved.setBoolean("IsBlacklist", false);
            NBTTagCompound settings = new NBTTagCompound();
            settings.setInteger("FilterMode", mode);
            saved.setTag("Filter", settings);
            fix(saved);

            CoverItemFilter cover = new CoverItemFilter(null, holder(), EnumFacing.UP, "legacy", null);
            cover.readFromNBT(saved);
            SmartItemFilter filter = (SmartItemFilter) cover.getFilterContainer().getFilter();
            assertNotNull(filter);
            assertFalse(filter.isBlacklistFilter());
            assertEquals(mode == 2 ? SmartItemFilter.SmartFilteringMode.VALUES[2] :
                    SmartItemFilter.SmartFilteringMode.ELECTROLYZER, filter.getFilteringMode());
        }
    }

    @Test
    void pumpKeepsItsFluidFilterAndDropsFluidsTheGameNoLongerHas() {
        NBTTagCompound saved = legacyPump();
        fix(saved);

        CoverPump pump = new CoverPump(null, holder(), EnumFacing.UP, 1, 1280);
        pump.readFromNBT(saved);
        assertEquals(CoverPump.PumpMode.IMPORT, pump.getPumpMode());
        SimpleFluidFilter filter = (SimpleFluidFilter) pump.getFluidFilterContainer().getFilter();
        assertNotNull(filter);
        assertTrue(filter.isBlacklistFilter());
        assertNotNull(filter.getFilterReader().getFluidStack(2));
        assertEquals(FluidRegistry.WATER, filter.getFilterReader().getFluidStack(2).getFluid());
        assertNull(filter.getFilterReader().getFluidStack(3));
    }

    @Test
    void regulatorAmountSavedBeforeTheMarkerBecomesEachFluidsAmount() {
        NBTTagCompound saved = cover("gregtech:fluid.regulator.lv");
        saved.setInteger("TransferMode", 2);
        saved.setInteger("TransferAmount", 250);
        saved.setTag("Filter", legacyContainer(290, false, fluidSettings(fluid("water", 1, 0))));
        fix(saved);
        assertFalse(saved.hasKey("TransferAmount"));
        NBTTagList slots = filterTag(saved.getCompoundTag("Filter")).getTagList("FilterSlots",
                Constants.NBT.TAG_COMPOUND);
        assertEquals(250, slots.getCompoundTagAt(0).getCompoundTag("Fluid").getInteger("Amount"));

        CoverFluidRegulator regulator = new CoverFluidRegulator(null, holder(), EnumFacing.UP, 1, 1000);
        regulator.readFromNBT(saved);
        SimpleFluidFilter filter = (SimpleFluidFilter) regulator.getFluidFilterContainer().getFilter();
        assertNotNull(filter);
        assertEquals(250, filter.getFilterReader().getFluidStack(0).amount);

        // a regulator saved since the marker was written keeps its amounts
        NBTTagCompound marked = cover("gregtech:fluid.regulator.lv");
        marked.setTag("filterv2", new NBTTagCompound());
        marked.setInteger("TransferAmount", 250);
        NBTTagCompound before = marked.copy();
        fix(marked);
        assertEquals(before, marked);
    }

    @Test
    void currentCoversAndUnknownFilterItemsAreLeftAsTheyLoad() {
        CoverConveyor conveyor = new CoverConveyor(null, holder(), EnumFacing.UP, 1, 64);
        conveyor.getItemFilterContainer().setFilterStack(MetaItems.ITEM_FILTER.getStackForm());
        conveyor.getItemFilterContainer().setBlacklistFilter(true);
        NBTTagCompound current = cover("gregtech:conveyor.lv");
        conveyor.writeToNBT(current);
        NBTTagCompound copy = current.copy();
        fix(copy);
        assertEquals(current, copy);

        CoverItemFilter filterCover = new CoverItemFilter(null, holder(), EnumFacing.UP, "legacy", null);
        filterCover.getFilterContainer().setFilterStack(MetaItems.ITEM_FILTER.getStackForm());
        NBTTagCompound currentFilterCover = cover("gregtech:item_filter");
        filterCover.writeToNBT(currentFilterCover);
        copy = currentFilterCover.copy();
        fix(copy);
        assertEquals(currentFilterCover, copy);

        // an item GTCEu's legacy filters never were keeps its stack; the legacy settings have nowhere to go
        NBTTagCompound unknown = cover("gregtech:conveyor.lv");
        NBTTagCompound container = new NBTTagCompound();
        container.setTag("FilterInventory", handler(1, item(0, "minecraft:stick", 1)));
        container.setBoolean("IsBlacklist", true);
        container.setTag("Filter", new NBTTagCompound());
        unknown.setTag("Filter", container);
        fix(unknown);
        assertEquals(handler(1, item(0, "minecraft:stick", 1)),
                unknown.getCompoundTag("Filter").getCompoundTag("FilterInventory"));
        assertEquals(Set.of("FilterInventory"), unknown.getCompoundTag("Filter").getKeySet());
    }

    private static @NotNull NBTTagCompound legacyConveyor() {
        NBTTagCompound conveyor = cover("gregtech:conveyor.lv");
        conveyor.setInteger("TransferRate", 7);
        conveyor.setInteger("ConveyorMode", CoverConveyor.ConveyorMode.IMPORT.ordinal());
        conveyor.setInteger("DistributionMode", 0);
        conveyor.setBoolean("WorkingAllowed", true);
        conveyor.setInteger("ManualImportExportMode", 0);
        NBTTagCompound slots = handler(9, item(0, "minecraft:iron_ingot", 64), item(4, "minecraft:gold_ingot", 2));
        NBTTagCompound counts = new NBTTagCompound();
        counts.setInteger("0", 100);
        slots.setTag("BigStackSize", counts);
        NBTTagCompound settings = new NBTTagCompound();
        settings.setTag("ItemFilter", slots);
        settings.setBoolean("IgnoreDamage", false);
        settings.setBoolean("IgnoreNBT", true);
        NBTTagCompound container = legacyContainer(291, false, settings);
        container.setInteger("MaxStackSize", 64);
        container.setInteger("TransferStackSize", 5);
        conveyor.setTag("Filter", container);
        return conveyor;
    }

    private static @NotNull NBTTagCompound legacyPump() {
        NBTTagCompound pump = cover("gregtech:pump.lv");
        pump.setInteger("TransferRate", 640);
        pump.setInteger("PumpMode", CoverPump.PumpMode.IMPORT.ordinal());
        pump.setTag("Filter", legacyContainer(290, true,
                fluidSettings(fluid("water", 1000, 2), fluid("legacy_missing_fluid", 5, 3))));
        return pump;
    }

    private static @NotNull NBTTagCompound legacyContainer(int filterItem, boolean blacklist,
                                                           @NotNull NBTTagCompound settings) {
        NBTTagCompound filter = item(0, "gregtech:meta_item_1", 1);
        filter.setShort("Damage", (short) filterItem);
        NBTTagCompound container = new NBTTagCompound();
        container.setTag("FilterInventory", handler(1, filter));
        container.setBoolean("IsBlacklist", blacklist);
        container.setTag("Filter", settings);
        return container;
    }

    private static @NotNull NBTTagCompound fluidSettings(@NotNull NBTTagCompound... fluids) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound fluid : fluids) {
            list.appendTag(fluid);
        }
        NBTTagCompound settings = new NBTTagCompound();
        settings.setTag("FluidFilter", list);
        return settings;
    }

    private static @NotNull NBTTagCompound fluid(@NotNull String name, int amount, int slot) {
        NBTTagCompound fluid = new NBTTagCompound();
        fluid.setString("FluidName", name);
        fluid.setInteger("Amount", amount);
        fluid.setInteger("Slot", slot);
        return fluid;
    }

    private static @NotNull NBTTagCompound filterTag(@NotNull NBTTagCompound container) {
        return container.getCompoundTag("FilterInventory").getTagList("Items", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).getCompoundTag("tag");
    }

    private static void fix(@NotNull NBTTagCompound cover) {
        V6LegacyDataFixer.fixCover(new ResourceLocation(cover.getString("CoverId")), cover);
    }

    private static @NotNull NBTTagCompound cover(@NotNull String id) {
        NBTTagCompound cover = new NBTTagCompound();
        cover.setString("CoverId", id);
        cover.setByte("Side", (byte) EnumFacing.UP.getIndex());
        return cover;
    }

    private static @NotNull NBTTagCompound chunk(@NotNull NBTTagCompound... tiles) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound tile : tiles) {
            list.appendTag(tile);
        }
        NBTTagCompound level = new NBTTagCompound();
        level.setTag("TileEntities", list);
        NBTTagCompound chunk = new NBTTagCompound();
        chunk.setTag("Level", level);
        return chunk;
    }

    private static @NotNull NBTTagCompound machineTile(@NotNull NBTTagCompound cover) {
        NBTTagCompound machine = new NBTTagCompound();
        machine.setTag("Covers", list(cover));
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "gregtech:machine");
        tile.setString("MetaId", "gregtech:hull.lv");
        tile.setTag("MetaTileEntity", machine);
        return tile;
    }

    private static @NotNull NBTTagCompound pipeTile(@NotNull NBTTagCompound cover) {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "gregtech:fluid_pipe");
        tile.setTag("Covers", list(cover));
        return tile;
    }

    private static @NotNull NBTTagList list(@NotNull NBTTagCompound entry) {
        NBTTagList list = new NBTTagList();
        list.appendTag(entry);
        return list;
    }

    private static @NotNull NBTTagCompound item(int slot, @NotNull String id, int count) {
        NBTTagCompound item = new NBTTagCompound();
        item.setInteger("Slot", slot);
        item.setString("id", id);
        item.setByte("Count", (byte) count);
        item.setShort("Damage", (short) 0);
        return item;
    }

    private static @NotNull NBTTagCompound handler(int size, @NotNull NBTTagCompound... items) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound item : items) {
            list.appendTag(item);
        }
        NBTTagCompound handler = new NBTTagCompound();
        handler.setTag("Items", list);
        handler.setInteger("Size", size);
        return handler;
    }

    private static @NotNull MetaTileEntityHull holder() {
        return new MetaTileEntityHull(gregtechId("legacy_cover_holder"), 1);
    }
}

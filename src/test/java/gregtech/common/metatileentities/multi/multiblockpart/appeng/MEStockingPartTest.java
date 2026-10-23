package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.ConfigHolder;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.multi.multiblockpart.appeng.MEStockingConfigTest.settings;
import static org.junit.jupiter.api.Assertions.*;

class MEStockingPartTest {

    private static MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        if (previousCircuit == null) {
            // The shared bootstrap creates meta items but does not register their subitems.
            MetaItem<?> metaItem = MetaItems.ITEMS.get(0);
            MetaItems.INTEGRATED_CIRCUIT = metaItem.getItem("circuit.integrated");
            if (MetaItems.INTEGRATED_CIRCUIT == null) {
                MetaItems.INTEGRATED_CIRCUIT = metaItem.addItem(461, "circuit.integrated");
            }
        }
    }

    @AfterAll
    static void restoreCircuit() {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
    }

    @Test
    void thresholdsUseSimulatedExtractableAmountsAndPreserveManualIdentity() {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        bus.readConfigFromTag(settings(10, 200));
        hatch.readConfigFromTag(settings(10, 200));
        IAEItemStack item = item(100);
        IAEFluidStack fluid = fluid(100);
        bus.storage.contents.add(item);
        hatch.storage.contents.add(fluid);
        bus.slots()[0].setConfig(item.copy().setStackSize(1));
        hatch.slots()[0].setConfig(fluid.copy().setStackSize(1));
        for (int available : new int[] { 9, 10, 11, 0 }) {
            bus.storage.extractionLimit = available;
            hatch.storage.extractionLimit = available;
            bus.syncME();
            hatch.syncME();
            assertNotNull(bus.slots()[0].getConfig());
            assertNotNull(hatch.slots()[0].getConfig());
            if (available < 10) {
                assertNull(bus.slots()[0].getStock());
                assertNull(hatch.slots()[0].getStock());
            } else {
                assertEquals(available, bus.slots()[0].getStock().getStackSize());
                assertEquals(available, hatch.slots()[0].getStock().getStackSize());
            }
        }
        assertEquals(100, item.getStackSize());
        assertEquals(100, fluid.getStackSize());
        assertEquals(0, bus.storage.modulations);
        assertEquals(0, hatch.storage.modulations);
    }

    @Test
    void productionUpdatesKeepBothLegacyClocksAndReplaceBothInCustomMode() {
        int previousInterval = ConfigHolder.compat.ae2.updateIntervals;
        try {
            ConfigHolder.compat.ae2.updateIntervals = 40;
            for (int interval : new int[] { 0, 1, 200 }) {
                StockingTestBus bus = new StockingTestBus();
                StockingTestHatch hatch = new StockingTestHatch();
                NBTTagCompound tag = settings(1, interval);
                tag.setBoolean("AutoPull", true);
                bus.readConfigFromTag(tag);
                hatch.readConfigFromTag(tag);
                bus.offset = 17;
                hatch.offset = 17;
                bus.advance(400);
                hatch.advance(400);
                int expectedSyncs = interval == 0 ? 14 : 400 / interval;
                int expectedSelections = interval == 0 ? 4 : expectedSyncs;
                assertEquals(expectedSyncs, bus.syncs, "bus interval " + interval);
                assertEquals(expectedSyncs, hatch.syncs, "hatch interval " + interval);
                assertEquals(expectedSelections, bus.storage.listScans);
                assertEquals(expectedSelections, hatch.storage.listScans);
                assertEquals(400, bus.statusChecks);
                assertEquals(400, hatch.statusChecks);
            }
        } finally {
            ConfigHolder.compat.ae2.updateIntervals = previousInterval;
        }
    }

    @Test
    void disconnectedAndDisabledPartsClearStockBetweenScheduledCycles() {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        bus.readConfigFromTag(settings(1, 200));
        hatch.readConfigFromTag(settings(1, 200));
        bus.slots()[0].setConfig(item(1));
        hatch.slots()[0].setConfig(fluid(1));
        bus.slots()[0].setStock(item(64));
        hatch.slots()[0].setStock(fluid(1000));
        bus.connected = false;
        hatch.connected = false;
        bus.advance(1);
        hatch.advance(1);
        assertNull(bus.slots()[0].getStock());
        assertNull(hatch.slots()[0].getStock());
        assertNotNull(bus.slots()[0].getConfig());
        assertNotNull(hatch.slots()[0].getConfig());
        bus.connected = true;
        hatch.connected = true;
        bus.setWorkingEnabled(false);
        hatch.setWorkingEnabled(false);
        bus.slots()[0].setStock(item(64));
        hatch.slots()[0].setStock(fluid(1000));
        bus.advance(1);
        hatch.advance(1);
        assertNull(bus.slots()[0].getStock());
        assertNull(hatch.slots()[0].getStock());
        assertEquals(0, bus.syncs);
        assertEquals(0, hatch.syncs);
        assertEquals(0, bus.storage.simulations);
        assertEquals(0, hatch.storage.simulations);
    }

    @Test
    void automaticSelectionSkipsSmallOrInaccessibleStocksWithoutChangingOrder() {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        bus.addToMultiBlock(null);
        hatch.addToMultiBlock(null);
        NBTTagCompound tag = settings(10, 1);
        tag.setBoolean("AutoPull", true);
        bus.readConfigFromTag(tag);
        hatch.readConfigFromTag(tag);
        bus.storage.contents.add(WrappedItemStack.fromItemStack(new ItemStack(Items.APPLE, 9)));
        hatch.storage.contents.add(WrappedFluidStack.fromFluidStack(new FluidStack(FluidRegistry.LAVA, 9)));
        bus.storage.contents.add(item(10));
        hatch.storage.contents.add(fluid(10));
        bus.advance(1);
        hatch.advance(1);
        assertEquals(Items.IRON_INGOT, bus.slots()[0].getConfig().createItemStack().getItem());
        assertEquals(FluidRegistry.WATER, hatch.slots()[0].getConfig().getFluid());
        assertNull(bus.slots()[1].getConfig());
        assertNull(hatch.slots()[1].getConfig());
        bus.storage.extractionLimit = 9;
        hatch.storage.extractionLimit = 9;
        bus.advance(1);
        hatch.advance(1);
        assertNull(bus.slots()[0].getConfig());
        assertNull(hatch.slots()[0].getConfig());
        assertEquals(0, bus.storage.modulations);
        assertEquals(0, hatch.storage.modulations);
    }

    @Test
    void cardPasteAppliesAllSettingsBeforeOneRefreshWithoutExtracting() {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        bus.addToMultiBlock(null);
        hatch.addToMultiBlock(null);
        bus.storage.contents.add(item(10));
        hatch.storage.contents.add(fluid(10));
        NBTTagCompound tag = settings(10, 200);
        tag.setBoolean("AutoPull", true);
        tag.setByte("GhostCircuit", (byte) 0);
        bus.readConfigFromTag(tag);
        hatch.readConfigFromTag(tag);
        assertEquals(0, bus.syncs);
        assertEquals(0, hatch.syncs);
        assertEquals(0, bus.storage.listScans);
        assertEquals(0, hatch.storage.listScans);
        EntityPlayer player = player();
        assertTrue(bus.onDataStickRightClick(player, card("MEInputBus", tag)));
        assertTrue(hatch.onDataStickRightClick(player, card("MEInputHatch", tag)));
        assertEquals(1, bus.syncs);
        assertEquals(1, hatch.syncs);
        assertEquals(1, bus.storage.listScans);
        assertEquals(1, hatch.storage.listScans);
        assertEquals(2, bus.storage.simulations);
        assertEquals(2, hatch.storage.simulations);
        assertEquals(0, bus.storage.modulations);
        assertEquals(0, hatch.storage.modulations);
        assertEquals(10, bus.slots()[0].getStock().getStackSize());
        assertEquals(10, hatch.slots()[0].getStock().getStackSize());
        assertEquals(10, bus.storage.contents.get(0).getStackSize());
        assertEquals(10, hatch.storage.contents.get(0).getStackSize());
        assertEquals(0, bus.writeConfigToTag().getByte("GhostCircuit"));
        tag.removeTag("MinimumStock");
        tag.removeTag("StockingRefreshTicks");
        tag.setByte("GhostCircuit", (byte) -1);
        bus.readConfigFromTag(tag);
        hatch.readConfigFromTag(tag);
        assertEquals(10, bus.writeConfigToTag().getInteger("MinimumStock"));
        assertEquals(200, hatch.writeConfigToTag().getInteger("StockingRefreshTicks"));
        assertEquals(-1, bus.writeConfigToTag().getByte("GhostCircuit"));
    }

    @Test
    void worldRoundTripRetainsSettingsAndOldWorldDefaultsRemainLegacy() {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        bus.readConfigFromTag(settings(100, 200));
        hatch.readConfigFromTag(settings(1000, 1));
        StockingTestBus loadedBus = new StockingTestBus();
        StockingTestHatch loadedHatch = new StockingTestHatch();
        loadedBus.readFromNBT(bus.writeToNBT(new NBTTagCompound()));
        loadedHatch.readFromNBT(hatch.writeToNBT(new NBTTagCompound()));
        assertEquals(100, loadedBus.writeConfigToTag().getInteger("MinimumStock"));
        assertEquals(200, loadedBus.writeConfigToTag().getInteger("StockingRefreshTicks"));
        assertEquals(1000, loadedHatch.writeConfigToTag().getInteger("MinimumStock"));
        assertEquals(1, loadedHatch.writeConfigToTag().getInteger("StockingRefreshTicks"));
        loadedBus.readFromNBT(new NBTTagCompound());
        loadedHatch.readFromNBT(new NBTTagCompound());
        assertEquals(1, loadedBus.writeConfigToTag().getInteger("MinimumStock"));
        assertEquals(0, loadedBus.writeConfigToTag().getInteger("StockingRefreshTicks"));
        assertEquals(1, loadedHatch.writeConfigToTag().getInteger("MinimumStock"));
        assertEquals(0, loadedHatch.writeConfigToTag().getInteger("StockingRefreshTicks"));
    }

    @Test
    void customIntervalUsesOffsetAndDoesNotRetainGlobalPolling() {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        bus.readConfigFromTag(settings(1, 200));
        hatch.readConfigFromTag(settings(1, 200));
        bus.offset = 17;
        hatch.offset = 17;
        bus.advance(182);
        hatch.advance(182);
        assertEquals(0, bus.syncs);
        assertEquals(0, hatch.syncs);
        bus.advance(1);
        hatch.advance(1);
        assertEquals(1, bus.syncs);
        assertEquals(1, hatch.syncs);
    }

    @Test
    void itemDuplicateGuardDistinctModeDetachAndReformStillApplyToAutoPull() {
        StockingTestController controller = new StockingTestController();
        StockingTestBus first = new StockingTestBus();
        StockingTestBus second = new StockingTestBus();
        controller.inputs.add(first.itemAbility());
        controller.inputs.add(second.itemAbility());
        first.controller = controller;
        second.controller = controller;
        first.addToMultiBlock(controller);
        second.addToMultiBlock(controller);
        first.slots()[0].setConfig(item(1));
        second.storage.contents.add(item(10));
        second.storage.contents.add(WrappedItemStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 20)));
        NBTTagCompound tag = settings(10, 1);
        tag.setBoolean("AutoPull", true);
        second.readConfigFromTag(tag);
        second.advance(1);
        assertEquals(Items.GOLD_INGOT, second.slots()[0].getConfig().createItemStack().getItem());
        assertNull(second.slots()[1].getConfig());
        controller.distinct = true;
        second.advance(1);
        assertEquals(Items.IRON_INGOT, second.slots()[0].getConfig().createItemStack().getItem());
        controller.distinct = false;
        second.onDistinctChange(false);
        assertNull(second.slots()[0].getConfig());
        second.removeFromMultiBlock(controller);
        second.controller = null;
        second.advance(1);
        assertNull(second.slots()[0].getConfig());
        second.controller = controller;
        second.addToMultiBlock(controller);
        second.advance(1);
        assertEquals(Items.GOLD_INGOT, second.slots()[0].getConfig().createItemStack().getItem());
    }

    @Test
    void fluidDuplicateGuardAndUnavailableMonitorDoNotExposePhantomStock() {
        StockingTestController controller = new StockingTestController();
        StockingTestHatch first = new StockingTestHatch();
        StockingTestHatch second = new StockingTestHatch();
        Collections.addAll(controller.fluids, first.slots());
        Collections.addAll(controller.fluids, second.slots());
        first.controller = controller;
        second.controller = controller;
        first.addToMultiBlock(controller);
        second.addToMultiBlock(controller);
        first.slots()[0].setConfig(fluid(1));
        second.storage.contents.add(fluid(10));
        second.storage.contents.add(WrappedFluidStack.fromFluidStack(new FluidStack(FluidRegistry.LAVA, 20)));
        NBTTagCompound tag = settings(10, 1);
        tag.setBoolean("AutoPull", true);
        second.readConfigFromTag(tag);
        second.advance(1);
        assertEquals(FluidRegistry.LAVA, second.slots()[0].getConfig().getFluid());
        assertNull(second.slots()[1].getConfig());
        second.monitorAvailable = false;
        second.advance(1);
        assertNull(second.slots()[0].getConfig());
        assertNull(second.slots()[0].getStock());
    }

    @Test
    void automaticSelectionKeepsFirstSixteenEligibleTypesWithoutAbundanceSorting() {
        StockingTestBus bus = new StockingTestBus();
        bus.addToMultiBlock(null);
        NBTTagCompound tag = settings(10, 1);
        tag.setBoolean("AutoPull", true);
        bus.readConfigFromTag(tag);
        bus.storage.contents.add(WrappedItemStack.fromItemStack(new ItemStack(Items.APPLE, 9)));
        for (int i = 0; i < 16; i++) {
            bus.storage.contents.add(WrappedItemStack.fromItemStack(new ItemStack(Items.DYE, 10 + i, i)));
        }
        bus.storage.contents.add(item(1000000));
        bus.advance(1);
        for (int i = 0; i < 16; i++) {
            ItemStack selected = bus.slots()[i].getConfig().createItemStack();
            assertEquals(Items.DYE, selected.getItem());
            assertEquals(i, selected.getMetadata());
        }
        assertEquals(1, bus.storage.listScans);
        assertEquals(32, bus.storage.simulations);
        assertEquals(0, bus.storage.modulations);
    }

    @Test
    void ordinaryInputHooksRetainOriginalPollingOrderAndLayout() {
        List<String> calls = new ArrayList<>();
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(
                gregtechId("ordinary_bus"), 5) {

            @Override
            public boolean updateMEStatus() {
                calls.add("status");
                return true;
            }

            @Override
            protected boolean shouldSyncME() {
                calls.add("sync");
                return true;
            }
        };
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(
                gregtechId("ordinary_hatch"), 5) {

            @Override
            public boolean updateMEStatus() {
                calls.add("status");
                return true;
            }

            @Override
            protected boolean shouldSyncME() {
                calls.add("sync");
                return true;
            }
        };
        assertTrue(bus.shouldUpdateME());
        assertEquals(Arrays.asList("status", "sync"), calls);
        calls.clear();
        assertTrue(hatch.shouldUpdateME());
        assertEquals(Arrays.asList("sync", "status"), calls);
        assertEquals(0, bus.getUIExtraHeight());
        assertEquals(0, hatch.getUIExtraHeight());
        bus.setWorkingEnabled(false);
        hatch.setWorkingEnabled(false);
        calls.clear();
        assertFalse(bus.shouldUpdateME());
        assertFalse(hatch.shouldUpdateME());
        assertTrue(calls.isEmpty());
    }

    private static IAEItemStack item(int amount) {
        return WrappedItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, amount));
    }

    private static IAEFluidStack fluid(int amount) {
        return WrappedFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, amount));
    }

    private static ItemStack card(String key, NBTTagCompound settings) {
        ItemStack card = new ItemStack(Items.PAPER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(key, settings);
        card.setTagCompound(tag);
        return card;
    }

    private static EntityPlayer player() {
        return new EntityPlayer(DummyWorld.INSTANCE, new GameProfile(UUID.randomUUID(), "stocking_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void sendStatusMessage(ITextComponent message, boolean actionBar) {}
        };
    }
}

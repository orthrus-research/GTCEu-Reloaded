package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.filter.CompositeFilterTestSupport;
import gregtech.common.covers.filter.IFilter.FilterType;
import gregtech.common.covers.filter.SimpleItemFilter;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamItemBus;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputFilterPartTest {

    private static CompositeFilterTestSupport filters;

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        filters = new CompositeFilterTestSupport();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        filters.close();
    }

    @Test
    void itemFilterChangesRejectMatchingTopupsWithoutRemovingExistingContents() {
        MetaTileEntityItemBus bus = bus();
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 32);
        assertTrue(bus.getExportItems().insertItem(0, iron, false).isEmpty());
        bus.getOutputFilter().setFilterStack(CompositeFilterTestSupport.itemLeaf(new ItemStack(Items.GOLD_INGOT)));
        ItemHandlerList combined = new ItemHandlerList(Collections.singletonList(bus.getExportItems()));
        assertFalse(combined.isItemValid(0, iron));
        assertFalse(combined.isItemValid(1, iron));
        assertFalse(GTTransferUtils.addItemsToItemHandler(combined, true, Collections.singletonList(iron)));
        assertEquals(32, combined.insertItem(0, iron, true).getCount());
        assertEquals(32, combined.insertItem(0, iron, false).getCount());
        assertEquals(32, combined.extractItem(0, 32, false).getCount());
        assertTrue(combined.getStackInSlot(0).isEmpty());
        bus.getOutputFilter().extractItem(0, 1, false);
        assertTrue(GTTransferUtils.addItemsToItemHandler(combined, true, Collections.singletonList(iron)));
    }

    @Test
    void itemSimulationAndActualRoutingAgreeAcrossFilteredAndUnfilteredBuses() {
        MetaTileEntityItemBus iron = bus();
        MetaTileEntityItemBus gold = bus();
        iron.getOutputFilter().setFilterStack(CompositeFilterTestSupport.itemLeaf(new ItemStack(Items.IRON_INGOT)));
        gold.getOutputFilter().setFilterStack(CompositeFilterTestSupport.itemLeaf(new ItemStack(Items.GOLD_INGOT)));
        ItemStackHandler fallback = new ItemStackHandler(1);
        ItemHandlerList combined = new ItemHandlerList(
                Arrays.asList(iron.getExportItems(), gold.getExportItems(), fallback));
        List<ItemStack> products = Arrays.asList(new ItemStack(Items.GOLD_INGOT, 40),
                new ItemStack(Items.IRON_INGOT, 50), new ItemStack(Items.DIAMOND, 4));
        assertTrue(GTTransferUtils.addItemsToItemHandler(combined, true, products));
        assertTrue(iron.getExportItems().getStackInSlot(0).isEmpty());
        GTTransferUtils.addItemsToItemHandler(combined, false, products);
        assertEquals(50, iron.getExportItems().getStackInSlot(0).getCount());
        assertEquals(40, gold.getExportItems().getStackInSlot(0).getCount());
        assertEquals(4, fallback.getStackInSlot(0).getCount());
        assertEquals(Items.DIAMOND, fallback.getStackInSlot(0).getItem());
    }

    @Test
    void itemSimulationTopsUpLaterMatchingStackBeforeEarlierEmptySlot() {
        ItemStackHandler inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 63));
        List<ItemStack> products = Arrays.asList(new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Items.IRON_INGOT, 64));
        assertTrue(GTTransferUtils.addItemsToItemHandler(inventory, true, products));
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertEquals(63, inventory.getStackInSlot(1).getCount());
        GTTransferUtils.addItemsToItemHandler(inventory, false, products);
        assertEquals(Items.IRON_INGOT, inventory.getStackInSlot(0).getItem());
        assertEquals(64, inventory.getStackInSlot(0).getCount());
        assertEquals(64, inventory.getStackInSlot(1).getCount());
    }

    @Test
    void duplicateLargeOutputsCannotOverflowAdmissionCounts() {
        ItemStackHandler inventory = new ItemStackHandler(1);
        List<ItemStack> products = Arrays.asList(new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE),
                new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE));
        assertFalse(GTTransferUtils.addItemsToItemHandler(inventory, true, products));
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertEquals(Integer.MAX_VALUE, products.get(0).getCount());
        assertEquals(Integer.MAX_VALUE, products.get(1).getCount());
    }

    @Test
    void fluidFilterRejectsExistingTopupsAndPrioritizesFilteredEmptyTanks() {
        MetaTileEntityMultiFluidHatch hatch = hatch(4);
        FluidStack water = new FluidStack(FluidRegistry.WATER, 200);
        hatch.getExportFluids().fill(water, true);
        hatch.getOutputFilter()
                .setFilterStack(CompositeFilterTestSupport.fluidLeaf(new FluidStack(FluidRegistry.LAVA, 1)));
        assertFalse(GTTransferUtils.addFluidsToFluidHandler(hatch.getExportFluids(), true,
                Collections.singletonList(water)));
        assertEquals(0, hatch.getExportFluids().fill(water, false));
        assertEquals(0, hatch.getExportFluids().fill(water, true));
        assertEquals(200, hatch.getExportFluids().drain(water, true).amount);
        FluidTank fallback = new FluidTank(1000);
        List<net.minecraftforge.fluids.IFluidTank> tanks = new ArrayList<>();
        tanks.add(fallback);
        tanks.addAll(hatch.getExportFluids().getFluidTanks());
        FluidTankList combined = new FluidTankList(true, tanks);
        FluidStack lava = new FluidStack(FluidRegistry.LAVA, 300);
        assertTrue(GTTransferUtils.addFluidsToFluidHandler(combined, true, Collections.singletonList(lava)));
        assertEquals(300, combined.fill(lava, true));
        assertEquals(0, fallback.getFluidAmount());
        assertEquals(300, hatch.getExportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void installedFilterEditsNotifyTheActualOutputHandlers() {
        RecipeFailureTestMachine receiver = new RecipeFailureTestMachine();
        MetaTileEntityItemBus bus = bus();
        ((NotifiableItemStackHandler) bus.getExportItems()).addNotifiableMetaTileEntity(receiver);
        bus.getOutputFilter().setFilterStack(CompositeFilterTestSupport.itemLeaf(new ItemStack(Items.IRON_INGOT)));
        assertTrue(receiver.getNotifiedItemOutputList().contains(bus.getExportItems()));
        receiver.getNotifiedItemOutputList().clear();
        ((SimpleItemFilter) bus.getOutputFilter().getFilter()).getFilterReader()
                .setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        assertTrue(receiver.getNotifiedItemOutputList().contains(bus.getExportItems()));
        assertFalse(bus.getExportItems().isItemValid(0, new ItemStack(Items.IRON_INGOT)));

        MetaTileEntityMultiFluidHatch hatch = hatch(9);
        for (IMultipleTankHandler.ITankEntry entry : hatch.getExportFluids()) {
            ((NotifiableFluidTank) entry.getDelegate()).addNotifiableMetaTileEntity(receiver);
        }
        hatch.getOutputFilter()
                .setFilterStack(CompositeFilterTestSupport.fluidLeaf(new FluidStack(FluidRegistry.WATER, 1)));
        assertEquals(9, receiver.getNotifiedFluidOutputList().size());
        receiver.getNotifiedFluidOutputList().clear();
        hatch.getOutputFilter().setBlacklistFilter(true);
        assertEquals(9, receiver.getNotifiedFluidOutputList().size());
    }

    @Test
    void filterNbtAndPacketsKeepExistingRejectedContentsAndSinglePhysicalDrop() {
        MetaTileEntityItemBus source = bus();
        ItemStack parent = CompositeFilterTestSupport.rawParent(FilterType.ITEM,
                CompositeFilterTestSupport.itemLeaf(new ItemStack(Items.GOLD_INGOT)));
        source.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 12));
        source.getOutputFilter().setFilterStack(parent);
        NBTTagCompound saved = source.writeToNBT(new NBTTagCompound());
        MetaTileEntityItemBus loaded = bus();
        loaded.readFromNBT(saved);
        assertEquals(12, loaded.getExportItems().getStackInSlot(0).getCount());
        assertEquals(source.getOutputFilter().serializeNBT(), loaded.getOutputFilter().serializeNBT());
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        source.writeInitialSyncData(packet);
        MetaTileEntityItemBus client = bus();
        client.receiveInitialSyncData(packet);
        assertEquals(0, packet.readableBytes());
        assertEquals(source.getOutputFilter().getFilterStack().serializeNBT(),
                client.getOutputFilter().getFilterStack().serializeNBT());
        assertEquals(1, client.getOutputFilter().getTransferSize(),
                "The existing filter packet reader clamps the unused transfer-size scalar to one");
        packet.release();
        List<ItemStack> drops = new ArrayList<>();
        loaded.clearMachineInventory(drops);
        loaded.clearMachineInventory(drops);
        assertEquals(2, drops.size());
        assertEquals(12, drops.get(0).getCount());
        assertEquals(parent.serializeNBT(), drops.get(1).serializeNBT());
        assertTrue(loaded.getOutputFilter().getFilterStack().isEmpty());
    }

    @Test
    void fluidSaveAndInitialSyncPreserveNonmatchingTankContents() {
        MetaTileEntityMultiFluidHatch source = hatch(4);
        source.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 456), true);
        source.getOutputFilter()
                .setFilterStack(CompositeFilterTestSupport.fluidLeaf(new FluidStack(FluidRegistry.LAVA, 1)));
        MetaTileEntityMultiFluidHatch loaded = hatch(4);
        loaded.readFromNBT(source.writeToNBT(new NBTTagCompound()));
        assertEquals(456, loaded.getExportFluids().getTankAt(0).getFluidAmount());
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        source.writeInitialSyncData(packet);
        loaded.receiveInitialSyncData(packet);
        assertEquals(0, packet.readableBytes());
        assertEquals(456, loaded.getExportFluids().getTankAt(0).getFluidAmount());
        assertEquals(source.getOutputFilter().serializeNBT(), loaded.getOutputFilter().serializeNBT());
        packet.release();
        List<ItemStack> drops = new ArrayList<>();
        loaded.clearMachineInventory(drops);
        loaded.clearMachineInventory(drops);
        assertEquals(1, drops.size());
        assertEquals(MetaItems.FLUID_FILTER.getStackForm().getItem(), drops.get(0).getItem());
    }

    @Test
    void missingFilterDataInputsSteamAndUnknownSubclassesKeepLegacyBehavior() {
        MetaTileEntityItemBus legacy = bus();
        legacy.readFromNBT(new NBTTagCompound());
        assertFalse(legacy.getOutputFilter().hasFilter());
        assertTrue(legacy.getExportItems().insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty());
        assertNull(new MetaTileEntityItemBus(GTUtility.gregtechId("input"), 1, false).getOutputFilter());
        assertNull(new MetaTileEntitySteamItemBus(GTUtility.gregtechId("steam"), true).getOutputFilter());
        assertNull(new MetaTileEntityItemBus(GTUtility.gregtechId("addon"), 1, true) {}.getOutputFilter());
        assertNull(
                new MetaTileEntityMultiFluidHatch(GTUtility.gregtechId("fluid_input"), 1, 4, false).getOutputFilter());
        assertNull(new MetaTileEntityMultiFluidHatch(GTUtility.gregtechId("fluid_addon"), 1, 4, true) {}
                .getOutputFilter());
    }

    private static MetaTileEntityItemBus bus() {
        return new MetaTileEntityItemBus(GTUtility.gregtechId("filtered_output_test"), 1, true);
    }

    private static MetaTileEntityMultiFluidHatch hatch(int slots) {
        return new MetaTileEntityMultiFluidHatch(GTUtility.gregtechId("filtered_fluid_output_test"), 1, slots, true);
    }
}

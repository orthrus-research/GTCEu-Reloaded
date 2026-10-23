package gregtech.api.metatileentity;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.virtualregistry.EntryTypes;
import gregtech.api.util.virtualregistry.VirtualEnderRegistry;
import gregtech.api.util.virtualregistry.VirtualEntry;
import gregtech.api.util.virtualregistry.entries.VirtualTank;
import gregtech.common.covers.CoverItemFilter;
import gregtech.common.covers.CoverPump;
import gregtech.common.covers.ItemFilterMode;
import gregtech.common.covers.ender.CoverEnderFluidLink;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static gregtech.api.metatileentity.AutoOutputSchedulingTest.stocked;
import static org.junit.jupiter.api.Assertions.*;

class AutoOutputCoverCompatibilityTest {

    @BeforeAll
    static void bootstrap() {
        AutoOutputTestMachine.bootstrap();
    }

    @Test
    void filterCoverUsesPublicPushAndObservesInPlaceSettingsOnTheNextPhase() {
        AutoOutputTestMachine machine = stocked();
        machine.setAutoOutputFluids(false);
        ItemStackHandler target = new ItemStackHandler(1);
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        neighbor.items = target;
        machine.neighbors.put(EnumFacing.SOUTH, neighbor);
        CoverItemFilter filter = new CoverItemFilter(itemFilterDefinition(), machine, EnumFacing.SOUTH, "test", null);
        machine.addCover(EnumFacing.SOUTH, filter);
        machine.ticks(1, 20); // Default insert-only filter denies extraction.
        assertEquals(4, machine.itemPushes);
        assertEquals(4, neighbor.itemQueries.size());
        assertEquals(0, target.getStackInSlot(0).getCount());
        filter.setFilterMode(ItemFilterMode.FILTER_EXTRACT);
        machine.ticks(21, 25); // No scheduling wake is needed for in-place cover edits.
        assertEquals(5, machine.itemPushes);
        assertEquals(8, target.getStackInSlot(0).getCount());
        machine.ticks(26, 30);
        assertEquals(6, machine.itemPushes); // Covered faces retain the existing path even after raw exports empty.
        assertEquals(6, neighbor.itemQueries.size());
    }

    @Test
    void pumpCoverRetainsItsDrainRestrictionsAndThePublicPushPath() {
        AutoOutputTestMachine machine = stocked();
        machine.setAutoOutputItems(false);
        FluidTank target = new FluidTank(1000);
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        neighbor.fluids = target;
        machine.neighbors.put(EnumFacing.EAST, neighbor);
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("auto_output_pump"),
                (def, view, side) -> new CoverPump(def, view, side, 1, 1000), ItemStack.EMPTY);
        CoverPump pump = new CoverPump(definition, machine, EnumFacing.EAST, 1, 1000);
        pump.setWorkingEnabled(false); // Isolate the machine's push from the cover's own transfer callback.
        pump.setPumpMode(CoverPump.PumpMode.IMPORT);
        machine.addCover(EnumFacing.EAST, pump);
        machine.ticks(1, 10);
        assertEquals(2, machine.fluidPushes);
        assertEquals(0, target.getFluidAmount());
        pump.setPumpMode(CoverPump.PumpMode.EXPORT);
        machine.tick(15);
        assertEquals(3, machine.fluidPushes);
        assertEquals(800, target.getFluidAmount());
        assertEquals(0, machine.getExportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void enderCoverCanExportItsVirtualSourceWhileTheMachineExportTankIsEmpty() {
        String name = "EFLink#" + VirtualEntry.DEFAULT_COLOR;
        VirtualTank previous = VirtualEnderRegistry.getEntry(null, EntryTypes.ENDER_FLUID, name);
        NBTTagCompound saved = previous == null ? null : previous.serializeNBT().copy();
        try {
            AutoOutputTestMachine machine = new AutoOutputTestMachine();
            machine.setAutoOutputFluids(true);
            FluidTank target = new FluidTank(1000);
            AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
            neighbor.fluids = target;
            machine.neighbors.put(EnumFacing.EAST, neighbor);
            CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("auto_output_ender"),
                    CoverEnderFluidLink::new, ItemStack.EMPTY);
            CoverEnderFluidLink cover = new CoverEnderFluidLink(definition, machine, EnumFacing.EAST);
            cover.setWorkingEnabled(false);
            VirtualTank virtual = (VirtualTank) cover.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                    null);
            virtual.setFluid(new FluidStack(FluidRegistry.WATER, 600));
            assertEquals(0, machine.getExportFluids().getTankAt(0).getFluidAmount());
            machine.addCover(EnumFacing.EAST, cover);
            machine.ticks(1, 20);
            assertEquals(4, machine.fluidPushes);
            assertEquals(4, neighbor.fluidQueries.size());
            assertEquals(600, target.getFluidAmount());
            assertEquals(0, virtual.getFluidAmount());
            assertEquals(0, machine.getExportFluids().getTankAt(0).getFluidAmount());
        } finally {
            if (previous == null) VirtualEnderRegistry.deleteEntry(null, EntryTypes.ENDER_FLUID, name);
            else previous.deserializeNBT(saved);
        }
    }

    @Test
    void coverPlacementAndRemovalInvalidateTheUncoveredNegativeCache() {
        AutoOutputTestMachine machine = stocked();
        machine.setAutoOutputFluids(false);
        machine.tick(5);
        assertEquals(1, machine.neighborQueries.size());
        CoverItemFilter filter = new CoverItemFilter(itemFilterDefinition(), machine, EnumFacing.SOUTH, "test", null);
        machine.addCover(EnumFacing.SOUTH, filter);
        machine.tick(10);
        assertEquals(1, machine.itemPushes);
        machine.removeCover(EnumFacing.SOUTH);
        ItemStackHandler target = new ItemStackHandler(1);
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        neighbor.items = target;
        machine.neighbors.put(EnumFacing.SOUTH, neighbor);
        machine.tick(15);
        assertEquals(8, target.getStackInSlot(0).getCount());
        assertEquals(1, machine.itemPushes);
        assertEquals(Collections.singletonList(EnumFacing.NORTH), neighbor.itemQueries);
    }

    @Test
    void sharedTransferHelperReportsAvailabilityRatherThanMovedQuantityAndKeepsSidedResolution() {
        AutoOutputTestMachine machine = new AutoOutputTestMachine();
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        machine.neighbors.put(EnumFacing.SOUTH, neighbor);
        assertFalse(machine.transferToNearbyAndCheckAvailability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                GTTransferUtils::moveInventoryItems, EnumFacing.SOUTH));
        ItemStackHandler full = new ItemStackHandler(1);
        full.setStackInSlot(0, new ItemStack(Items.COAL, 64));
        neighbor.items = full;
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        assertTrue(machine.transferToNearbyAndCheckAvailability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                GTTransferUtils::moveInventoryItems, EnumFacing.SOUTH));
        assertEquals(8, machine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(64, full.getStackInSlot(0).getCount());
        assertEquals(2, neighbor.itemQueries.size());
        for (EnumFacing side : neighbor.itemQueries) assertEquals(EnumFacing.NORTH, side);
        AutoOutputTestMachine noLocal = new AutoOutputTestMachine() {

            @Override
            public <T> T getCapability(Capability<T> capability, EnumFacing side) {
                return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? null :
                        super.getCapability(capability, side);
            }
        };
        noLocal.neighbors.put(EnumFacing.SOUTH, neighbor);
        assertFalse(noLocal.transferToNearbyAndCheckAvailability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                (source, destination) -> fail("Missing local capability must not transfer"), EnumFacing.SOUTH));
    }

    private static CoverDefinition itemFilterDefinition() {
        return new CoverDefinition(GTUtility.gregtechId("auto_output_item_filter"),
                (def, view, side) -> new CoverItemFilter(def, view, side, "test", null), ItemStack.EMPTY);
    }
}

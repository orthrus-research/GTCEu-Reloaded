package gregtech.api.metatileentity;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.capability.impl.ElectricItem;
import gregtech.api.util.GTUtility;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoOutputSchedulingTest {

    @BeforeAll
    static void bootstrap() {
        AutoOutputTestMachine.bootstrap();
    }

    @Test
    void emptyLanesSkipAllNeighborProbesWhileExcludedMachinesKeepPublicPushes() {
        AutoOutputTestMachine admitted = new AutoOutputTestMachine();
        AutoOutputTestMachine excluded = new AutoOutputTestMachine();
        excluded.scheduling = false;
        for (AutoOutputTestMachine machine : new AutoOutputTestMachine[] { admitted, excluded }) {
            machine.setAutoOutputItems(true);
            machine.setAutoOutputFluids(true);
            machine.ticks(1, 100);
        }
        assertEquals(0, admitted.neighborQueries.size());
        assertEquals(40, excluded.neighborQueries.size());
        assertEquals(20, excluded.itemPushes);
        assertEquals(20, excluded.fluidPushes);
        assertEquals(100, admitted.coverUpdates);
        assertEquals(excluded.weatherChecks, admitted.weatherChecks);
        assertEquals(excluded.writeToNBT(new NBTTagCompound()), admitted.writeToNBT(new NBTTagCompound()));
    }

    @Test
    void absentNeighborsAndMissingCapabilitiesRetryEveryTwentyTicksIndependently() {
        AutoOutputTestMachine machine = stocked();
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        machine.neighbors.put(EnumFacing.EAST, neighbor);
        machine.ticks(1, 100);
        assertEquals(10, machine.neighborQueries.size());
        assertEquals(5, neighbor.fluidQueries.size());
        assertEquals(0, neighbor.itemQueries.size());
        assertEquals(8, machine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(800, machine.getExportFluids().getTankAt(0).getFluidAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 7, 19 })
    void notificationWakeUsesTheNextOriginalFiveTickPhase(int start) {
        AutoOutputTestMachine machine = new AutoOutputTestMachine();
        machine.setAutoOutputItems(true);
        machine.setAutoOutputFluids(true);
        ItemStackHandler target = new ItemStackHandler(2);
        FluidTank tank = new FluidTank(2000);
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        neighbor.items = target;
        neighbor.fluids = tank;
        machine.neighbors.put(EnumFacing.SOUTH, neighbor);
        machine.neighbors.put(EnumFacing.EAST, neighbor);
        machine.ticks(start, start + 6);
        assertTrue(machine.neighborQueries.isEmpty());
        int changed = start + 7;
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        machine.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 300), true);
        int due = changed + Math.floorMod(-changed, 5);
        machine.ticks(changed, due - 1);
        assertEquals(0, target.getStackInSlot(0).getCount());
        assertEquals(0, tank.getFluidAmount());
        machine.tick(due);
        assertEquals(3, target.getStackInSlot(0).getCount());
        assertEquals(300, tank.getFluidAmount());
        assertEquals(Collections.singletonList(EnumFacing.NORTH), neighbor.itemQueries);
        assertEquals(Collections.singletonList(EnumFacing.WEST), neighbor.fluidQueries);
        machine.ticks(due + 1, due + 25);
        assertEquals(2, machine.neighborQueries.size());
    }

    @Test
    void silentCapabilityRecoveryWaitsAtMostTwentyTicksAndNeighborWakeIsImmediateAtNextPhase() {
        AutoOutputTestMachine silent = stocked();
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        silent.neighbors.put(EnumFacing.SOUTH, neighbor);
        silent.tick(5);
        ItemStackHandler target = new ItemStackHandler(1);
        neighbor.items = target;
        silent.ticks(6, 24);
        assertEquals(0, target.getStackInSlot(0).getCount());
        silent.tick(25);
        assertEquals(8, target.getStackInSlot(0).getCount());
        assertEquals(2, neighbor.itemQueries.size());

        AutoOutputTestMachine notified = stocked();
        notified.tick(5);
        ItemStackHandler immediate = new ItemStackHandler(1);
        AutoOutputTestNeighbor replacement = new AutoOutputTestNeighbor();
        replacement.items = immediate;
        notified.neighbors.put(EnumFacing.SOUTH, replacement);
        notified.onNeighborChanged();
        notified.ticks(6, 9);
        assertEquals(0, immediate.getStackInSlot(0).getCount());
        notified.tick(10);
        assertEquals(8, immediate.getStackInSlot(0).getCount());
    }

    @Test
    void connectedFullDestinationsAreNeverNegativeCachedAndCapabilitiesAreReacquired() {
        AutoOutputTestMachine machine = stocked();
        ItemStackHandler full = new ItemStackHandler(1);
        full.setStackInSlot(0, new ItemStack(Items.COAL, 64));
        FluidTank tank = new FluidTank(new FluidStack(FluidRegistry.LAVA, 1000), 1000);
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        neighbor.items = full;
        neighbor.fluids = tank;
        machine.neighbors.put(EnumFacing.SOUTH, neighbor);
        machine.neighbors.put(EnumFacing.EAST, neighbor);
        machine.ticks(1, 20);
        assertEquals(4, neighbor.itemQueries.size());
        assertEquals(4, neighbor.fluidQueries.size());
        assertEquals(8, machine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(800, machine.getExportFluids().getTankAt(0).getFluidAmount());
        ItemStackHandler replacement = new ItemStackHandler(1);
        FluidTank replacementTank = new FluidTank(1000);
        neighbor.items = replacement;
        neighbor.fluids = replacementTank;
        machine.tick(25);
        assertEquals(8, replacement.getStackInSlot(0).getCount());
        assertEquals(800, replacementTank.getFluidAmount());
        assertEquals(64, full.getStackInSlot(0).getCount());
        assertEquals(1000, tank.getFluidAmount());
        assertEquals(5, neighbor.itemQueries.size());
        assertEquals(5, neighbor.fluidQueries.size());
    }

    @Test
    void changingOneOutputDoesNotWakeTheOtherLaneOrConsumeRecipeNotifications() {
        AutoOutputTestMachine machine = stocked();
        machine.tick(5);
        List<?> itemNotifications = new ArrayList<>(machine.getNotifiedItemOutputList());
        List<?> fluidNotifications = new ArrayList<>(machine.getNotifiedFluidOutputList());
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9));
        machine.tick(10);
        assertEquals(3, machine.neighborQueries.size());
        assertEquals(EnumFacing.SOUTH, machine.neighborQueries.get(2));
        assertEquals(itemNotifications, machine.getNotifiedItemOutputList());
        assertEquals(fluidNotifications, machine.getNotifiedFluidOutputList());
        machine.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 100), true);
        machine.tick(15);
        assertEquals(4, machine.neighborQueries.size());
        assertEquals(EnumFacing.EAST, machine.neighborQueries.get(3));
    }

    @Test
    void aNotificationRaisedDuringTheProbeSurvivesItsUnavailableResult() {
        AutoOutputTestMachine machine = stocked();
        machine.setAutoOutputFluids(false);
        AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
        machine.neighbors.put(EnumFacing.SOUTH, neighbor);
        neighbor.onItemQuery = () -> {
            neighbor.onItemQuery = null;
            machine.addNotifiedOutput(machine.getExportItems());
        };
        machine.tick(5);
        machine.tick(10);
        assertEquals(2, neighbor.itemQueries.size());
        machine.tick(15);
        assertEquals(2, neighbor.itemQueries.size());
    }

    @Test
    void clockRollbackLargeJumpsAndLoadCannotExtendAnAbsentRetry() {
        AutoOutputTestMachine machine = stocked();
        machine.setAutoOutputFluids(false);
        machine.tick(100);
        machine.tick(105);
        assertEquals(1, machine.neighborQueries.size());
        machine.tick(10);
        assertEquals(2, machine.neighborQueries.size());
        machine.tick(1000000);
        assertEquals(3, machine.neighborQueries.size());
        machine.onLoad();
        machine.tick(1000005);
        assertEquals(4, machine.neighborQueries.size());
        NBTTagCompound saved = machine.writeToNBT(new NBTTagCompound());
        machine.readFromNBT(saved.copy());
        assertEquals(saved, machine.writeToNBT(new NBTTagCompound()));
        machine.tick(1000010);
        assertEquals(5, machine.neighborQueries.size());
        machine.tick(Long.MAX_VALUE - 2); // Exactly divisible by five.
        assertEquals(6, machine.neighborQueries.size());
        machine.tick(Long.MIN_VALUE + 3); // The first divisible-by-five phase after overflow.
        assertEquals(6, machine.neighborQueries.size());
        machine.tick(Long.MIN_VALUE + 18);
        assertEquals(7, machine.neighborQueries.size());
    }

    @Test
    void settingsFacingEnableAndExplicitWakeInvalidateOnlyAtAnOutputPhase() {
        AutoOutputTestMachine machine = stocked();
        machine.setAutoOutputFluids(false);
        machine.tick(5);
        machine.setAutoOutputItems(false);
        machine.ticks(6, 40);
        assertEquals(1, machine.neighborQueries.size());
        machine.setAutoOutputItems(true);
        machine.tick(45);
        machine.setOutputFacingItems(EnumFacing.WEST);
        machine.tick(50);
        machine.setAllowInputFromOutputSideItems(true);
        machine.tick(55);
        machine.setFrontFacing(EnumFacing.UP);
        machine.tick(60);
        machine.setOutputFacing(EnumFacing.DOWN);
        machine.tick(65);
        for (int i = 0; i < 100; i++) machine.requestAutoOutputWakeup();
        assertEquals(6, machine.neighborQueries.size());
        machine.tick(66);
        assertEquals(6, machine.neighborQueries.size());
        machine.tick(70);
        assertEquals(7, machine.neighborQueries.size());
        assertEquals(EnumFacing.WEST, machine.neighborQueries.get(2));
        assertEquals(EnumFacing.DOWN, machine.neighborQueries.get(6));
    }

    @Test
    void fluidSettingsWakeTheirLaneWhileTheItemAbsenceRemainsCached() {
        AutoOutputTestMachine machine = stocked();
        machine.tick(5);
        machine.setOutputFacingFluids(EnumFacing.WEST);
        machine.tick(10);
        machine.setAllowInputFromOutputSideFluids(true);
        machine.tick(15);
        machine.setAutoOutputFluids(false);
        machine.tick(20);
        assertEquals(4, machine.neighborQueries.size());
        machine.setAutoOutputFluids(true);
        machine.tick(25);
        assertEquals(6, machine.neighborQueries.size());
        assertEquals(EnumFacing.WEST, machine.neighborQueries.get(2));
        assertEquals(EnumFacing.WEST, machine.neighborQueries.get(3));
        assertEquals(EnumFacing.WEST, machine.neighborQueries.get(4));
        assertEquals(EnumFacing.SOUTH, machine.neighborQueries.get(5));
    }

    @Test
    void connectedTransfersMatchContinuousBalancesAndFiveTickTiming() {
        AutoOutputTestMachine admitted = new AutoOutputTestMachine();
        AutoOutputTestMachine continuous = new AutoOutputTestMachine();
        continuous.scheduling = false;
        ItemStackHandler[] targets = { new ItemStackHandler(2), new ItemStackHandler(2) };
        FluidTank[] tanks = { new FluidTank(4000), new FluidTank(4000) };
        AutoOutputTestMachine[] machines = { admitted, continuous };
        for (int i = 0; i < machines.length; i++) {
            AutoOutputTestNeighbor neighbor = new AutoOutputTestNeighbor();
            neighbor.items = targets[i];
            neighbor.fluids = tanks[i];
            machines[i].neighbors.put(EnumFacing.SOUTH, neighbor);
            machines[i].neighbors.put(EnumFacing.EAST, neighbor);
            machines[i].setAutoOutputItems(true);
            machines[i].setAutoOutputFluids(true);
            machines[i].setAllowInputFromOutputSideItems(true);
            machines[i].setAllowInputFromOutputSideFluids(true);
            machines[i].getImportItems().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 4));
            machines[i].getImportFluids().fill(new FluidStack(FluidRegistry.LAVA, 100), true);
        }
        for (int tick = 1; tick <= 80; tick++) {
            for (AutoOutputTestMachine machine : machines) {
                if (tick == 7 || tick == 46) {
                    machine.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
                    machine.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 300), true);
                }
                machine.tick(tick);
            }
            assertEquals(targets[1].serializeNBT(), targets[0].serializeNBT(), "tick " + tick);
            assertEquals(tanks[1].getFluidAmount(), tanks[0].getFluidAmount(), "tick " + tick);
            assertEquals(continuous.writeToNBT(new NBTTagCompound()), admitted.writeToNBT(new NBTTagCompound()),
                    "tick " + tick);
        }
        assertEquals(6, targets[0].getStackInSlot(0).getCount());
        assertEquals(600, tanks[0].getFluidAmount());
        assertEquals(4, admitted.getImportItems().getStackInSlot(0).getCount());
        assertEquals(100, admitted.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(4, admitted.neighborQueries.size());
        assertEquals(32, continuous.neighborQueries.size());
    }

    @Test
    void schedulingChangesNeitherBatteryTransferNorOtherTraitCallbacks() {
        AutoOutputTestMachine machine = new AutoOutputTestMachine();
        machine.setAutoOutputItems(true);
        machine.setAutoOutputFluids(true);
        List<Long> ticks = new ArrayList<>();
        new MTETrait(machine) {

            @Override
            public @NotNull String getName() {
                return "auto_output_other_trait";
            }

            @Override
            public <T> T getCapability(Capability<T> capability) {
                return null;
            }

            @Override
            public void update() {
                ticks.add(machine.getOffsetTimer());
            }
        };
        Item batteryItem = new Item() {

            @Override
            public @Nullable ICapabilityProvider initCapabilities(@NotNull ItemStack stack,
                                                                  @Nullable NBTTagCompound nbt) {
                return new ElectricItem(stack, 128, 1, true, true);
            }
        };
        ItemStack battery = new ItemStack(batteryItem);
        IElectricItem electric = battery.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        assertNotNull(electric);
        assertEquals(128, electric.charge(128, 1, true, false));
        machine.setBattery(battery);
        machine.ticks(1, 20);
        assertEquals(20, ticks.size());
        for (int i = 0; i < 20; i++) assertEquals(i + 1, ticks.get(i).longValue());
        assertEquals(128, machine.energy().getEnergyStored());
        assertEquals(128, machine.energy().getInputPerSec());
        assertEquals(0, electric.getCharge());
        assertEquals(0, machine.neighborQueries.size());
    }

    @Test
    void defaultAdmissionRequiresTheExactMachine() {
        SimpleMachineMetaTileEntity ordinary = new SimpleMachineMetaTileEntity(GTUtility.gregtechId("ordinary_output"),
                AutoOutputTestMachine.map(), null, 1, true);
        SimpleMachineMetaTileEntity unknown = new SimpleMachineMetaTileEntity(GTUtility.gregtechId("unknown_output"),
                AutoOutputTestMachine.map(), null, 1, true) {};
        assertTrue(ordinary.supportsAutoOutputScheduling());
        assertFalse(unknown.supportsAutoOutputScheduling());
    }

    static AutoOutputTestMachine stocked() {
        AutoOutputTestMachine machine = new AutoOutputTestMachine();
        machine.setAutoOutputItems(true);
        machine.setAutoOutputFluids(true);
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        machine.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 800), true);
        return machine;
    }
}

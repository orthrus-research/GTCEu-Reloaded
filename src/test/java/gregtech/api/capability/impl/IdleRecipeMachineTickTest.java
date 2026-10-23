package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.capability.impl.IdleRecipeSchedulingTest.*;
import static org.junit.jupiter.api.Assertions.*;

class IdleRecipeMachineTickTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        EnergyContainerHandlerTest.bootstrap();
    }

    @Test
    void sleepingRecipeKeepsOtherTraitsCoversBatteryTransfersAndAutoOutput() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        IdleRecipeCountingTrait other = new IdleRecipeCountingTrait(machine);
        machine.tick(1);
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
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.STICK, 3));
        machine.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 100), true);
        machine.setAutoOutputItems(true);
        machine.setAutoOutputFluids(true);
        for (int tick = 2; tick <= 20; tick++) machine.tick(tick);
        assertEquals(2, machine.logic().callbacks);
        assertEquals(20, other.ticks.size());
        assertEquals(20, machine.coverUpdates);
        assertEquals(20, machine.weatherChecks);
        assertEquals(4, machine.itemPushes);
        assertEquals(4, machine.fluidPushes);
        assertEquals(3, machine.adjacentItems.getStackInSlot(0).getCount());
        assertEquals(100, machine.adjacentFluid.getFluidAmount());
        assertEquals(0, electric.getCharge());
        assertEquals(128, machine.energy().getEnergyStored());
        assertEquals(128, machine.energy().getInputPerSec());
        assertEquals(0, machine.energy().energyInputPerSec);
    }

    @Test
    void holderTimerAndRepeatedCallbacksKeepOriginalOffsetAlignment() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        IdleRecipeCountingTrait other = new IdleRecipeCountingTrait(machine);
        machine.tick = -1;
        MetaTileEntityHolder holder = (MetaTileEntityHolder) machine.getHolder();
        long first = holder.getOffsetTimer();
        for (int i = 0; i < 41; i++) holder.update();
        assertEquals(first + 41, holder.getOffsetTimer());
        assertEquals(41, other.ticks.size());
        for (int i = 0; i < 41; i++) assertEquals(first + i, other.ticks.get(i).longValue());
        int aligned = 0;
        for (long tick = first; tick < first + 41; tick++) if (tick % 20 == 0) aligned++;
        assertEquals(aligned, machine.logic().conditions);
        assertEquals(aligned + (first % 20 == 0 ? 0 : 1), machine.logic().callbacks);
        machine.tick = 60;
        machine.update();
        machine.update(); // An allowed external accelerator uses the same holder offset until holder.update advances
                          // it.
        assertEquals(aligned + 2, machine.logic().conditions);
        assertEquals(43, other.ticks.size());
    }

    @Test
    void runningRecipeResultsMatchContinuousLogicAcrossIdleIntervals() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        IdleRecipeTestMachine sleeping = new IdleRecipeTestMachine(map);
        IdleRecipeTestMachine continuous = new IdleRecipeTestMachine(map);
        continuous.scheduling = false;
        sleeping.energy().setEnergyStored(1000);
        continuous.energy().setEnergyStored(1000);
        for (int tick = 1; tick <= 80; tick++) {
            if (tick == 11 || tick == 47) {
                sleeping.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
                continuous.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            }
            sleeping.tick(tick);
            continuous.tick(tick);
            assertEquals(continuous.logic().serializeNBT(), sleeping.logic().serializeNBT(), "tick " + tick);
            assertEquals(continuous.energy().getEnergyStored(), sleeping.energy().getEnergyStored(), "tick " + tick);
            assertEquals(continuous.getImportItems().getStackInSlot(0).getCount(),
                    sleeping.getImportItems().getStackInSlot(0).getCount(), "tick " + tick);
            assertEquals(continuous.getExportItems().getStackInSlot(0).getCount(),
                    sleeping.getExportItems().getStackInSlot(0).getCount(), "tick " + tick);
        }
        assertTrue(sleeping.logic().callbacks < continuous.logic().callbacks);
        assertEquals(continuous.logic().searches, sleeping.logic().searches);
        assertEquals(continuous.logic().finalChecks, sleeping.logic().finalChecks);
        assertEquals(2, sleeping.getExportItems().getStackInSlot(0).getCount());
    }
}

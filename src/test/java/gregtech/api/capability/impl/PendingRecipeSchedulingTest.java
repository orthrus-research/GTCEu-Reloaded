package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PendingRecipeSchedulingTest {

    private static MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        if (previousCircuit == null) {
            MetaItem<?> meta = MetaItems.ITEMS.get(0);
            MetaItems.INTEGRATED_CIRCUIT = meta.getItem("circuit.integrated");
            if (MetaItems.INTEGRATED_CIRCUIT == null) {
                MetaItems.INTEGRATED_CIRCUIT = meta.addItem(461, "circuit.integrated");
            }
        }
    }

    @AfterAll
    static void restore() {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
    }

    @Test
    void scheduledMachineRetriesEveryPendingTickIncludingReloadWithoutProcessingOrGateMutation() {
        RecipeMap<SimpleRecipeBuilder> map = IdleRecipeSchedulingTest.map();
        IdleRecipeSchedulingTest.add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.tick(1);
        fillOutputs(machine);
        for (int tick = 2; tick <= 9; tick++) machine.tick(tick);
        assertTrue(machine.logic().isAwaitingOutputs());
        assertFalse(machine.logic().isActive());
        long energy = machine.energy().getEnergyStored();
        int callbacks = machine.logic().callbacks;
        int conditions = machine.logic().conditions;
        machine.logic().beforeProgress = () -> { throw new AssertionError("Pending output invoked processing"); };
        for (int tick = 10; tick <= 40; tick++) {
            NBTTagCompound before = machine.logic().serializeNBT();
            int itemNotifications = machine.getNotifiedItemOutputList().size();
            for (int probe = 0; probe < 3; probe++) assertTrue(machine.logic().shouldUpdateRecipeLogic());
            assertEquals(before, machine.logic().serializeNBT());
            assertEquals(itemNotifications, machine.getNotifiedItemOutputList().size());
            machine.tick(tick);
        }
        assertEquals(callbacks + 31, machine.logic().callbacks);
        assertEquals(conditions, machine.logic().conditions);
        assertEquals(energy, machine.energy().getEnergyStored());
        NBTTagCompound saved = machine.logic().serializeNBT();

        IdleRecipeTestMachine loaded = new IdleRecipeTestMachine(map);
        loaded.energy().setEnergyStored(energy);
        fillOutputs(loaded);
        loaded.logic().deserializeNBT(saved);
        loaded.logic().beforeProgress = machine.logic().beforeProgress;
        assertFalse(loaded.logic().isActive());
        for (int tick = 41; tick <= 60; tick++) loaded.tick(tick);
        assertEquals(20, loaded.logic().callbacks);
        assertEquals(0, loaded.logic().conditions);
        assertEquals(saved.getTag("ItemOutputs"), loaded.logic().serializeNBT().getTag("ItemOutputs"));
        assertEquals(energy, loaded.energy().getEnergyStored());
        loaded.getExportItems().setStackInSlot(0, ItemStack.EMPTY);
        loaded.tick(61);
        assertEquals(21, loaded.logic().callbacks);
        assertFalse(loaded.logic().isAwaitingOutputs());
        assertEquals(0, loaded.logic().getProgress());
        assertEquals(Items.GOLD_INGOT, loaded.getExportItems().getStackInSlot(0).getItem());
        assertEquals(1, loaded.getExportItems().getStackInSlot(0).getCount());
        assertEquals(energy, loaded.energy().getEnergyStored());
    }

    private static void fillOutputs(IdleRecipeTestMachine machine) {
        for (int slot = 0; slot < machine.getExportItems().getSlots(); slot++) {
            machine.getExportItems().setStackInSlot(slot, new ItemStack(Items.STICK, 64));
        }
    }
}

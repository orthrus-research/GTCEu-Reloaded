package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.GTHashMaps;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingRecipeOutputTest {

    private static int nextMap;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void completedSampledOutputsWaitWithoutEnergyInputsRerollsOrCompletionHooks() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        PendingOutputTestMachine machine = machine();
        PendingOutputTestLogic logic = new PendingOutputTestLogic(machine, map);
        logic.trySearchNewRecipe();
        assertEquals(1, logic.setups);
        assertEquals(1, machine.getImportItems().getStackInSlot(0).getCount());
        List<ItemStack> sampled = logic.itemOutputs;
        NBTTagCompound original = logic.serializeNBT();
        machine.acceptItems = false;
        machine.acceptFluids = false;
        finishProcessing(machine, logic);
        assertTrue(logic.isAwaitingOutputs());
        assertFalse(logic.isActive());
        assertEquals(logic.getMaxProgress(), logic.getProgress());
        int draws = logic.actualDraws;
        int simulated = logic.simulatedDraws;
        logic.forbidProcessing = true;
        for (int tick = 20; tick <= 100; tick++) {
            machine.tick = tick;
            logic.update();
        }
        assertSame(sampled, logic.itemOutputs);
        assertEquals(original.getTag("ItemOutputs"), logic.serializeNBT().getTag("ItemOutputs"));
        assertEquals(draws, logic.actualDraws);
        assertEquals(simulated, logic.simulatedDraws);
        assertEquals(1, logic.setups);
        assertEquals(0, logic.completions);
        assertEquals(1, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(RecipeFailureReason.ITEM_OUTPUT_FULL, logic.getRecipeFailure().getReason());
        machine.acceptItems = true;
        logic.update();
        assertEquals(RecipeFailureReason.FLUID_OUTPUT_FULL, logic.getRecipeFailure().getReason());
        assertTrue(machine.getExportItems().getStackInSlot(0).isEmpty());
        machine.acceptFluids = true;
        logic.update();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(1, logic.completions);
        assertEquals(0, logic.getProgress());
        assertEquals(100, machine.getExportFluids().getTankAt(0).getFluidAmount());
        assertEquals(1, machine.getExportItems().getStackInSlot(0).getCount());
        assertFalse(logic.isActive());
        assertFalse(logic.wasActiveAndNeedsUpdate);
        assertEquals(draws, logic.actualDraws);
        logic.forbidProcessing = false;
        logic.trySearchNewRecipe();
        assertTrue(logic.isActive(), "A new recipe after an inactive completion must reactivate the controller");
        assertEquals(2, logic.setups);
    }

    @Test
    void pendingSaveLoadDisableAndInvalidationKeepTheirDistinctLifecycles() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        PendingOutputTestMachine original = machine();
        PendingOutputTestLogic first = new PendingOutputTestLogic(original, map);
        first.trySearchNewRecipe();
        original.acceptItems = false;
        finishProcessing(original, first);
        first.setWorkingEnabled(false);
        NBTTagCompound saved = first.serializeNBT();
        assertTrue(saved.getBoolean("AwaitingOutputs"));
        PendingOutputTestMachine loadedMachine = machine();
        PendingOutputTestLogic loaded = new PendingOutputTestLogic(loadedMachine, map);
        loaded.deserializeNBT(saved);
        assertTrue(loaded.isAwaitingOutputs());
        assertFalse(loaded.isActive());
        assertEquals(saved.getTag("ItemOutputs"), loaded.serializeNBT().getTag("ItemOutputs"));
        loaded.forbidProcessing = true;
        loadedMachine.tick = 20;
        loaded.update();
        assertEquals(0, loaded.completions);
        assertTrue(loadedMachine.getExportItems().getStackInSlot(0).isEmpty());
        loaded.setWorkingEnabled(true);
        loaded.update();
        assertEquals(1, loaded.completions);
        assertEquals(0, loaded.actualDraws);
        assertFalse(loaded.isAwaitingOutputs());
        loaded.deserializeNBT(saved);
        loaded.invalidate();
        assertFalse(loaded.isAwaitingOutputs());
        assertEquals(0, loaded.getProgress());
        assertTrue(loaded.itemOutputs.isEmpty());
        assertFalse(loaded.serializeNBT().hasKey("AwaitingOutputs"));

        NBTTagCompound oldWorld = saved.copy();
        oldWorld.removeTag("AwaitingOutputs");
        loaded.deserializeNBT(oldWorld);
        assertFalse(loaded.isAwaitingOutputs());
        assertTrue(loaded.isActive(), "Missing new state preserves the legacy running-recipe load behavior");
    }

    @Test
    void categoryVoidingOnlyDiscardsTheExplicitlyAllowedCategory() {
        for (boolean voidItems : new boolean[] { true, false }) {
            PendingOutputTestMachine machine = machine();
            PendingOutputTestLogic logic = new PendingOutputTestLogic(machine, map());
            logic.trySearchNewRecipe();
            machine.acceptItems = false;
            machine.acceptFluids = false;
            machine.voidItems = voidItems;
            machine.voidFluids = !voidItems;
            finishProcessing(machine, logic);
            assertTrue(logic.isAwaitingOutputs());
            assertEquals(voidItems ? RecipeFailureReason.FLUID_OUTPUT_FULL : RecipeFailureReason.ITEM_OUTPUT_FULL,
                    logic.getRecipeFailure().getReason());
            machine.acceptItems = !voidItems;
            machine.acceptFluids = voidItems;
            logic.update();
            assertFalse(logic.isAwaitingOutputs());
            assertEquals(1, logic.completions);
            assertEquals(voidItems, machine.getExportItems().getStackInSlot(0).isEmpty());
            assertEquals(voidItems ? 100 : 0, machine.getExportFluids().getTankAt(0).getFluidAmount());
        }
    }

    @Test
    void rejectedFilterAtRecipeStartDoesNotConsumeInputsOrEnergy() {
        PendingOutputTestMachine machine = machine();
        machine.acceptItems = false;
        PendingOutputTestLogic logic = new PendingOutputTestLogic(machine, map());
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(0, logic.actualDraws);
        assertEquals(RecipeFailureReason.ITEM_OUTPUT_FULL, logic.getRecipeFailure().getReason());
    }

    @Test
    void overlappingFiltersUseSampledOrderAndWaitInsteadOfDroppingTheSecondProduct() {
        List<ItemStack> products = new ArrayList<>(GTHashMaps.fromItemStackCollection(Arrays.asList(
                new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.GOLD_INGOT, 64))).keySet());
        // Deliberately oppose the old unordered admission traversal, independently of registry/hash order.
        Collections.reverse(products);
        products.replaceAll(stack -> new ItemStack(stack.getItem(), 64));
        PendingOutputTestMachine machine = machine();
        boolean[] broadenSecond = { false };
        ItemHandlerList outputs = new ItemHandlerList(Arrays.asList(
                new FilteredNotifiableItemStackHandler(machine, 1, machine, stack -> true),
                new FilteredNotifiableItemStackHandler(machine, 1, machine,
                        stack -> broadenSecond[0] || stack.getItem() == products.get(0).getItem())));
        PendingOutputTestLogic logic = new PendingOutputTestLogic(machine, map()) {

            @Override
            protected IItemHandlerModifiable getOutputInventory() {
                return outputs;
            }
        };
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.itemOutputs = products;
        logic.tryCompleteRecipe();
        assertTrue(logic.isAwaitingOutputs());
        assertEquals(0, logic.completions);
        assertTrue(outputs.getStackInSlot(0).isEmpty());
        assertTrue(outputs.getStackInSlot(1).isEmpty());
        assertSame(products, logic.itemOutputs);
        broadenSecond[0] = true;
        logic.tryCompleteRecipe();
        assertEquals(1, logic.completions);
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(products.get(0).getItem(), outputs.getStackInSlot(0).getItem());
        assertEquals(products.get(1).getItem(), outputs.getStackInSlot(1).getItem());
        assertEquals(64, outputs.getStackInSlot(0).getCount());
        assertEquals(64, outputs.getStackInSlot(1).getCount());
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("pending_output_" + nextMap++,
                new SimpleRecipeBuilder())
                        .itemInputs(1).itemOutputs(2).fluidOutputs(1).build();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.GOLD_INGOT)).chancedOutput(new ItemStack(Items.DIAMOND), 5000, 0)
                .fluidOutputs(new FluidStack(FluidRegistry.WATER, 100)).duration(3).EUt(1).build().getResult();
        assertTrue(map.compileRecipe(recipe));
        return map;
    }

    static PendingOutputTestMachine machine() {
        PendingOutputTestMachine machine = new PendingOutputTestMachine();
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        return machine;
    }

    static void finishProcessing(PendingOutputTestMachine machine, PendingOutputTestLogic logic) {
        for (int i = 0; i < 3; i++) {
            machine.tick = i + 1;
            logic.update();
        }
    }
}

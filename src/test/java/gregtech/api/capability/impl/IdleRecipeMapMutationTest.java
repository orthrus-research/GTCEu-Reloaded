package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.capability.impl.IdleRecipeSchedulingTest.*;
import static org.junit.jupiter.api.Assertions.*;

class IdleRecipeMapMutationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void onlySuccessfulCoreMutationsAdvanceTheTransientRevision() {
        IdleRecipeTrackedMap map = new IdleRecipeTrackedMap();
        assertEquals(0, map.getRecipeRevision());
        assertFalse(map.compileRecipe(null));
        map.clearRecipes();
        assertEquals(0, map.getRecipeRevision());
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        assertEquals(1, map.getRecipeRevision());
        Recipe conflict = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.DIAMOND)).EUt(1).duration(8).build().getResult();
        assertFalse(map.compileRecipe(conflict));
        assertEquals(1, map.getRecipeRevision());
        assertTrue(map.removeRecipe(recipe));
        assertEquals(2, map.getRecipeRevision());
        assertFalse(map.removeRecipe(recipe));
        assertEquals(2, map.getRecipeRevision());
        assertTrue(map.compileRecipe(recipe));
        assertEquals(3, map.getRecipeRevision());
        map.clearRecipes();
        assertEquals(4, map.getRecipeRevision());
        map.clearRecipes();
        assertEquals(4, map.getRecipeRevision());
    }

    @Test
    void anIdleMapAdditionWakesWithoutInventoryChangesOrPredicateMutation() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.tick(1);
        assertFalse(machine.logic().shouldUpdateRecipeLogic());
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        NBTTagCompound before = machine.logic().serializeNBT();
        for (int i = 0; i < 100; i++) assertTrue(machine.logic().shouldUpdateRecipeLogic());
        assertEquals(before, machine.logic().serializeNBT());
        assertTrue(machine.logic().invalidInputsForRecipes);
        assertEquals(1, machine.logic().searches);
        assertTrue(machine.getNotifiedItemInputList().isEmpty());
        machine.tick(2);
        assertEquals(2, machine.logic().searches);
        assertSame(recipe, machine.logic().getPreviousRecipe());
        assertEquals(1, machine.logic().getProgress());
    }

    @Test
    void mapIdentityChangesRefreshOnlyTheIdleLookup() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.tick(1);
        RecipeMap<SimpleRecipeBuilder> replacement = map();
        Recipe recipe = add(replacement, replacement.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        machine.logic().selectedMap = replacement;
        assertTrue(machine.logic().shouldUpdateRecipeLogic());
        assertNull(machine.logic().getPreviousRecipe());
        machine.tick(2);
        assertSame(recipe, machine.logic().getPreviousRecipe());
        assertEquals(1, machine.logic().getProgress());
    }

    @Test
    void changesDuringProgressKeepSampledOutputsAndWaitForAnIdleEntry() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe original = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        machine.tick(1);
        assertSame(original, machine.logic().getPreviousRecipe());
        assertTrue(map.removeRecipe(original));
        Recipe replacement = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.DIAMOND)).EUt(1).duration(8).build().getResult();
        assertTrue(map.compileRecipe(replacement));
        NBTTagCompound before = machine.logic().serializeNBT();
        for (int i = 0; i < 50; i++) assertTrue(machine.logic().shouldUpdateRecipeLogic());
        assertEquals(before, machine.logic().serializeNBT());
        long tick = 2;
        while (machine.logic().getProgress() > 0 && tick < 30) machine.tick(tick++);
        assertEquals(0, machine.logic().getProgress());
        assertEquals(Items.GOLD_INGOT, machine.getExportItems().getStackInSlot(0).getItem());
        assertEquals(1, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(1, machine.logic().searches);
        machine.tick(tick);
        assertSame(replacement, machine.logic().getPreviousRecipe());
        assertEquals(1, machine.logic().getProgress());
        assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty());
        assertEquals(Items.DIAMOND, machine.logic().itemOutputs.get(0).getItem());
    }

    @Test
    void untrackedMapSubclassesRemainContinuousUntilTheyExplicitlyOptIn() {
        RecipeMap<SimpleRecipeBuilder> unknown = new RecipeMap<SimpleRecipeBuilder>("idle_unknown_map",
                new SimpleRecipeBuilder(), map -> new RecipeMapUI<>(map, true, true, true, true, false),
                2, 2, 1, 1) {};
        assertFalse(unknown.supportsRecipeRevisionTracking());
        IdleRecipeTestMachine continuous = new IdleRecipeTestMachine(unknown);
        IdleRecipeTestMachine optedIn = new IdleRecipeTestMachine(new IdleRecipeTrackedMap());
        for (int tick = 1; tick <= 10; tick++) {
            continuous.tick(tick);
            optedIn.tick(tick);
        }
        assertEquals(10, continuous.logic().callbacks);
        assertEquals(1, optedIn.logic().callbacks);
    }

    @Test
    void mutationInsideTheFinalProgressCallbackCannotRestartTheRemovedCachedRecipe() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        machine.tick(1);
        long tick = 2;
        while (machine.logic().getProgress() < machine.logic().getMaxProgress() && tick < 30) machine.tick(tick++);
        assertEquals(machine.logic().getMaxProgress(), machine.logic().getProgress());
        machine.logic().beforeProgress = () -> assertTrue(map.removeRecipe(recipe));
        machine.tick(tick++);
        machine.logic().beforeProgress = null;
        assertEquals(0, machine.logic().getProgress());
        assertEquals(1, machine.logic().searches);
        assertEquals(Items.GOLD_INGOT, machine.getExportItems().getStackInSlot(0).getItem());
        assertEquals(1, machine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(1, machine.getImportItems().getStackInSlot(0).getCount());
        assertTrue(machine.logic().shouldUpdateRecipeLogic());
        machine.tick(tick);
        assertEquals(2, machine.logic().searches);
        assertEquals(0, machine.logic().getProgress());
        assertNull(machine.logic().getPreviousRecipe());
        assertEquals(1, machine.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void disabledMapMutationRemainsPendingUntilEnableWithoutResourceChanges() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.tick(1);
        machine.logic().setWorkingEnabled(false);
        machine.tick(2);
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        NBTTagCompound before = machine.logic().serializeNBT();
        for (int tick = 3; tick <= 25; tick++) {
            assertFalse(machine.logic().shouldUpdateRecipeLogic());
            machine.tick(tick);
        }
        assertEquals(2, machine.logic().callbacks);
        assertEquals(before, machine.logic().serializeNBT());
        assertEquals(1000, machine.energy().getEnergyStored());
        machine.logic().setWorkingEnabled(true);
        machine.tick(26);
        assertEquals(3, machine.logic().callbacks);
        assertEquals(1, machine.logic().getProgress());
        assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty());
    }

    @Test
    void nullMapFallsBackToContinuousCallbacksAndReturningTrackedMapWakesLookup() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.logic().selectedMap = null;
        for (int tick = 1; tick <= 5; tick++) machine.tick(tick);
        assertEquals(5, machine.logic().callbacks);
        RecipeMap<SimpleRecipeBuilder> replacement = map();
        add(replacement, replacement.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.logic().selectedMap = replacement;
        machine.tick(6);
        assertEquals(1, machine.logic().getProgress());
        assertEquals(6, machine.logic().callbacks);
    }
}

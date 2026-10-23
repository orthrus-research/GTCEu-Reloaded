package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionRecipeRuntimeTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void midProcessLossFreezesExactWorkBeforeLegacyPredicatesResourcesAndVirtualProgress() {
        RecipeMap<SimpleRecipeBuilder> map = conditionedMap();
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.trySearchNewRecipe();
        machine.tick = 1;
        logic.update();
        int progress = logic.getProgress();
        int draws = logic.actualDraws;
        int simulations = logic.simulatedDraws;
        int progressCalls = logic.progressCalls;
        int legacyChecks = logic.legacyProgressChecks;
        long stored = logic.stored;
        List<ItemStack> sampled = logic.itemOutputs;
        NBTTagCompound saved = logic.serializeNBT();
        machine.environment.daytime = false;
        for (machine.tick = 2; machine.tick <= 50; machine.tick++) {
            assertTrue(logic.shouldUpdateRecipeLogic(), "Running holds must remain scheduled");
            logic.update();
        }
        assertEquals(progress, logic.getProgress());
        assertTrue(logic.isRecipeConditionPaused());
        assertFalse(logic.isActive());
        assertEquals(RecipeFailureReason.CONDITION, logic.getRecipeFailure().getReason());
        assertEquals(progressCalls, logic.progressCalls);
        assertEquals(legacyChecks, logic.legacyProgressChecks);
        assertEquals(draws, logic.actualDraws);
        assertEquals(simulations, logic.simulatedDraws);
        assertEquals(stored, logic.stored);
        assertSame(sampled, logic.itemOutputs);
        assertEquals(saved.getTag("ItemOutputs"), logic.serializeNBT().getTag("ItemOutputs"));
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(1, logic.setups);
        machine.environment.daytime = true;
        logic.update(); // Tick 51: recovery must refresh legacy predicates outside their periodic cadence.
        assertFalse(logic.isRecipeConditionPaused());
        assertTrue(logic.isActive());
        assertEquals(legacyChecks + 1, logic.legacyProgressChecks);
        assertEquals(progress + 1, logic.getProgress());
        assertEquals(draws + 1, logic.actualDraws);
        assertSame(sampled, logic.itemOutputs);
        while (logic.getProgress() > 0) {
            machine.tick++;
            logic.update();
        }
        assertEquals(1, logic.completions);
        assertFalse(logic.serializeNBT().hasKey("ActiveRecipeConditions"));
    }

    @Test
    void pauseSaveLoadAndDisableResumeUsePreparedSnapshotWithoutSearchOrStartup() {
        RecipeMap<SimpleRecipeBuilder> map = conditionedMap();
        ConditionRuntimeMachine firstMachine = ConditionRecipeSelectionTest.machine();
        ConditionRuntimeLogic first = new ConditionRuntimeLogic(firstMachine, map);
        first.trySearchNewRecipe();
        firstMachine.environment.daytime = false;
        firstMachine.tick = 3;
        first.update();
        first.setWorkingEnabled(false);
        NBTTagCompound saved = first.serializeNBT();
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        machine.environment.daytime = false;
        ConditionRuntimeLogic loaded = new ConditionRuntimeLogic(machine, map);
        loaded.deserializeNBT(saved);
        assertNull(loaded.previousRecipe);
        assertFalse(loaded.isActive());
        assertTrue(loaded.isRecipeConditionPaused());
        machine.tick = 21;
        loaded.update();
        assertEquals(0, machine.environment.conditionReads, "Disabled work must not evaluate conditions");
        loaded.setWorkingEnabled(true);
        loaded.update();
        assertEquals(saved.getInteger("Progress"), loaded.getProgress());
        assertEquals(0, loaded.actualDraws);
        assertEquals(saved.getTag("ActiveRecipeConditions"), loaded.serializeNBT().getTag("ActiveRecipeConditions"));
        machine.environment.daytime = true;
        loaded.update();
        assertEquals(saved.getInteger("Progress") + 1, loaded.getProgress());
        assertEquals(0, loaded.finalChecks);
        assertEquals(0, loaded.searches);
        assertEquals(0, loaded.setups);
        assertEquals(3, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(saved.getTag("ItemOutputs"), loaded.serializeNBT().getTag("ItemOutputs"));
    }

    @Test
    void unknownInvertedAndMalformedActiveSnapshotsPersistAsUnavailableHolds() {
        RecipeMap<SimpleRecipeBuilder> map = conditionedMap();
        ConditionRuntimeLogic original = new ConditionRuntimeLogic(ConditionRecipeSelectionTest.machine(), map);
        original.trySearchNewRecipe();
        NBTTagCompound unknown = original.serializeNBT();
        NBTTagCompound leaf = unknown.getCompoundTag("ActiveRecipeConditions").getTagList("Primary", 10)
                .getCompoundTagAt(0);
        leaf.setString("Type", "missing:future_condition");
        leaf.setBoolean("Inverted", true);
        for (boolean malformed : new boolean[] { false, true }) {
            NBTTagCompound input = unknown.copy();
            if (malformed) input.setString("ActiveRecipeConditions", "wrong-type");
            ConditionRuntimeLogic loaded = new ConditionRuntimeLogic(ConditionRecipeSelectionTest.machine(), map);
            loaded.deserializeNBT(input);
            loaded.update();
            assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE, loaded.getRecipeFailure().getReason());
            assertEquals(input.getInteger("Progress"), loaded.getProgress());
            assertEquals(0, loaded.progressCalls);
            assertEquals(0, loaded.actualDraws);
            assertFalse(loaded.isActive());
            NBTTagCompound saved = loaded.serializeNBT();
            if (!malformed)
                assertEquals(input.getTag("ActiveRecipeConditions"), saved.getTag("ActiveRecipeConditions"));
            loaded.deserializeNBT(saved);
            loaded.update();
            assertEquals(saved.getTag("ActiveRecipeConditions"),
                    loaded.serializeNBT().getTag("ActiveRecipeConditions"));
            assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE, loaded.getRecipeFailure().getReason());
        }
    }

    @Test
    void oldSavesWithoutConditionsRetainTheirExistingProgressPathAndInvalidationClearsNewState() {
        RecipeMap<SimpleRecipeBuilder> map = conditionedMap();
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.trySearchNewRecipe();
        NBTTagCompound oldSave = logic.serializeNBT();
        oldSave.removeTag("ActiveRecipeConditions");
        logic.deserializeNBT(oldSave);
        machine.environment.forbidConditionReads = true;
        machine.tick = 1;
        logic.update();
        assertFalse(logic.isRecipeConditionPaused());
        assertEquals(oldSave.getInteger("Progress") + 1, logic.getProgress());
        logic.invalidate();
        assertFalse(logic.isRecipeConditionPaused());
        assertEquals(0, logic.getProgress());
        assertFalse(logic.serializeNBT().hasKey("ActiveRecipeConditions"));
    }

    @Test
    void completedAwaitingOutputsBypassConditionsAcrossSaveDisableAndRecoveryExactlyOnce() {
        RecipeMap<SimpleRecipeBuilder> map = conditionedMap();
        ConditionRuntimeMachine firstMachine = ConditionRecipeSelectionTest.machine();
        ConditionRuntimeLogic first = new ConditionRuntimeLogic(firstMachine, map);
        first.trySearchNewRecipe();
        firstMachine.acceptItems = false;
        while (!first.isAwaitingOutputs()) {
            firstMachine.tick++;
            first.update();
        }
        first.setWorkingEnabled(false);
        NBTTagCompound saved = first.serializeNBT();
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        machine.acceptItems = false;
        machine.environment.daytime = false;
        machine.environment.forbidConditionReads = true;
        ConditionRuntimeLogic loaded = new ConditionRuntimeLogic(machine, map);
        loaded.deserializeNBT(saved);
        assertTrue(loaded.isAwaitingOutputs());
        assertFalse(loaded.isRecipeConditionPaused());
        loaded.update();
        loaded.setWorkingEnabled(true);
        for (machine.tick = 20; machine.tick <= 60; machine.tick++) loaded.update();
        assertEquals(0, loaded.progressCalls);
        assertEquals(0, loaded.legacyProgressChecks);
        assertEquals(0, loaded.actualDraws);
        assertEquals(0, loaded.simulatedDraws);
        assertEquals(0, loaded.completions);
        assertEquals(RecipeFailureReason.ITEM_OUTPUT_FULL, loaded.getRecipeFailure().getReason());
        assertEquals(saved.getTag("ItemOutputs"), loaded.serializeNBT().getTag("ItemOutputs"));
        machine.acceptItems = true;
        loaded.update();
        loaded.update();
        assertEquals(1, loaded.completions);
        assertEquals(1, machine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(0, loaded.getProgress());
        assertFalse(loaded.serializeNBT().hasKey("ActiveRecipeConditions"));
        assertEquals(0, loaded.finalChecks);
        assertEquals(0, loaded.setups);
    }

    @Test
    void observedDiagnosticGettersAndProbeEncodingNeverReevaluateTheWorld() {
        RecipeMap<SimpleRecipeBuilder> map = conditionedMap();
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        machine.environment.daytime = false;
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.trySearchNewRecipe();
        RecipeFailure observed = logic.getRecipeFailure();
        int reads = machine.environment.conditionReads;
        machine.environment.forbidConditionReads = true;
        for (int i = 0; i < 30; i++) {
            assertSame(observed, logic.getRecipeFailure());
            NBTTagCompound probe = new NBTTagCompound();
            observed.writeToNBT(probe);
            assertEquals(observed, RecipeFailure.readFromNBT(probe));
            observed.format((key, arguments) -> key);
        }
        assertEquals(reads, machine.environment.conditionReads);
        assertEquals(0, logic.setups);
    }

    private static RecipeMap<SimpleRecipeBuilder> conditionedMap() {
        RecipeMap<SimpleRecipeBuilder> map = ConditionRecipeSelectionTest.map();
        assertTrue(map.compileRecipe(ConditionRecipeSelectionTest.recipe(map).conditionDaytime().build().getResult()));
        return map;
    }
}

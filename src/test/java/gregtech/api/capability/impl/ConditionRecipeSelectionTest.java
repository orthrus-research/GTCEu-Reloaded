package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionRecipeSelectionTest {

    private static int nextMap;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void blockedCachedConditionSelectsEligibleAlternativeBeforeLegacySideEffects() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = recipe(map).conditionHeight(70, 90).build().getResult();
        Recipe allowed = map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT)).outputs(new ItemStack(Items.APPLE))
                .EUt(1).duration(8).conditionBiome("minecraft:plains").build().getResult();
        assertTrue(map.compileRecipe(blocked));
        assertTrue(map.compileRecipe(allowed));
        ConditionRuntimeMachine machine = machine();
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 3));
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.previousRecipe = blocked;
        logic.trySearchNewRecipe();
        assertSame(allowed, logic.previousRecipe);
        assertEquals(3, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(2, machine.getImportItems().getStackInSlot(1).getCount());
        assertEquals(1, logic.finalChecks);
        assertEquals(1, logic.setups);
    }

    @Test
    void environmentalRetryUsesIdleSafetyCadenceWithoutInventoryChanges() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        assertTrue(map.compileRecipe(recipe(map).conditionDaytime().build().getResult()));
        ConditionRuntimeMachine machine = machine();
        machine.environment.daytime = false;
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.allowSearch = true;
        logic.update();
        assertEquals(RecipeFailureReason.CONDITION, logic.getRecipeFailure().getReason());
        assertFalse(logic.invalidInputsForRecipes);
        assertEquals(0, logic.finalChecks);
        machine.environment.daytime = true;
        for (machine.tick = 1; machine.tick < 20; machine.tick++) {
            if (logic.shouldUpdateRecipeLogic()) logic.update();
        }
        assertEquals(1, logic.searches);
        assertEquals(0, logic.setups);
        assertTrue(logic.shouldUpdateRecipeLogic());
        logic.update();
        assertEquals(1, logic.setups);
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void conditionsStillGateUnknownLegacyOverridesWithoutProbingTheirSelectedCheck() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = recipe(map).conditionDaytime().build().getResult();
        ConditionRuntimeMachine machine = machine();
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.candidateOptIn = false;
        machine.environment.daytime = false;
        assertFalse(logic.isRecipeCandidateEligible(recipe));
        assertEquals(RecipeFailureReason.CONDITION, logic.evaluateRecipeCheck(recipe).getReason());
        assertEquals(0, logic.finalChecks);
        machine.environment.daytime = true;
        assertTrue(logic.isRecipeCandidateEligible(recipe));
        assertEquals(0, logic.finalChecks);
        assertTrue(logic.evaluateRecipeCheck(recipe).isEmpty());
        assertEquals(1, logic.finalChecks);
    }

    @Test
    void actualPreparedParallelRecipeIsCheckedBeforePowerAndInputConsumption() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe selected = recipe(map).conditionDaytime().build().getResult();
        assertTrue(map.compileRecipe(selected));
        ConditionRuntimeMachine machine = machine();
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.setParallelLimit(2);
        logic.extraPreparedCondition = RecipeConditions.height(100, 200, false);
        logic.trySearchNewRecipe();
        assertSame(selected, logic.previousRecipe);
        assertEquals(1, logic.finalChecks);
        assertEquals(0, logic.setups);
        assertEquals(0, logic.simulatedDraws);
        assertEquals(0, logic.actualDraws);
        assertEquals(3, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(RecipeFailureReason.CONDITION, logic.getRecipeFailure().getReason());
        assertFalse(logic.isActive());
    }

    @Test
    void runningSnapshotContainsPreparedParallelRequirementsInsteadOfOnlyTheCachedSource() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe selected = recipe(map).conditionDaytime().build().getResult();
        assertTrue(map.compileRecipe(selected));
        ConditionRuntimeMachine machine = machine();
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        logic.setParallelLimit(2);
        logic.extraPreparedCondition = RecipeConditions.height(0, 100, false);
        logic.trySearchNewRecipe();
        assertEquals(1, logic.setups);
        assertSame(selected, logic.previousRecipe);
        assertEquals(2,
                RecipeConditionSet.deserialize(logic.serializeNBT().getTag("ActiveRecipeConditions")).clauseCount());
        machine.position = new BlockPos(32, 200, 48);
        machine.tick = 1;
        logic.update();
        assertTrue(logic.isRecipeConditionPaused());
        assertEquals(1, logic.getProgress());
        assertEquals(0, logic.actualDraws);
    }

    @Test
    void absentMachinePositionIsUnavailableRatherThanAnInventedOriginOrAnException() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(new RecipeFailureTestMachine(), map);
        Recipe recipe = recipe(map).conditionHeight(0, 0).build().getResult();
        assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE, logic.evaluateRecipeEligibility(recipe).getReason());
        assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE, logic.evaluateRecipeCheck(recipe).getReason());
        assertEquals(0, logic.finalChecks);
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("condition_runtime_" + nextMap++, new SimpleRecipeBuilder())
                .itemInputs(8).itemOutputs(8).fluidOutputs(1).build();
    }

    static SimpleRecipeBuilder recipe(RecipeMap<SimpleRecipeBuilder> map) {
        return map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.GOLD_INGOT))
                .EUt(1).duration(8);
    }

    static ConditionRuntimeMachine machine() {
        ConditionRuntimeMachine machine = new ConditionRuntimeMachine();
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        return machine;
    }
}

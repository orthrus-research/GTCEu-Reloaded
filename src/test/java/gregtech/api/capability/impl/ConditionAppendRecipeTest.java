package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.ParallelLogic;
import gregtech.api.util.EnumValidationResult;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionAppendRecipeTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void compositionOverflowDoesNotPoisonEarlierWorkOrReserveOutputSpaceForRejectedRecipes() {
        RecipeMap<SimpleRecipeBuilder> map = ConditionRecipeSelectionTest.map();
        SimpleRecipeBuilder first = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(8);
        for (int i = 0; i < 16; i++) first.conditionHeight(i, 100 + i);
        Recipe admitted = first.build().getResult();
        assertTrue(map.compileRecipe(admitted));
        assertTrue(map.compileRecipe(map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.APPLE, 63)).EUt(1).duration(8).conditionHeight(-100, 200).build()
                .getResult()));
        SimpleRecipeBuilder last = map.recipeBuilder().inputs(new ItemStack(Items.COAL))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(8);
        for (int i = 0; i < 16; i++) last.conditionHeight(i, 100 + i);
        assertTrue(map.compileRecipe(last.build().getResult()));
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        ItemStackHandler inputs = new ItemStackHandler(3);
        inputs.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        inputs.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        inputs.setStackInSlot(2, new ItemStack(Items.COAL));
        ItemStackHandler outputs = new ItemStackHandler(1);
        RecipeBuilder<?> result = ParallelLogic.appendItemRecipes(map, inputs, outputs, 3, 32, machine,
                logic::isRecipeCandidateEligible);
        assertNotNull(result);
        assertEquals(2, result.getParallel());
        assertEquals(16, result.getRecipeConditions().clauseCount());
        assertEquals(EnumValidationResult.VALID, result.build().getType());
        Recipe combined = result.build().getResult();
        assertEquals(2, combined.getOutputs().stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(
                combined.getInputs().stream().noneMatch(input -> input.acceptsStack(new ItemStack(Items.GOLD_INGOT))));
        assertTrue(outputs.getStackInSlot(0).isEmpty());
        assertEquals(1, inputs.getStackInSlot(1).getCount());
        assertEquals(0, logic.finalChecks);
    }

    @Test
    void appendEligibilitySkipsBlockedInputsWithoutInvokingLegacyChecksAndOldOverloadRemainsUsable() {
        RecipeMap<SimpleRecipeBuilder> map = ConditionRecipeSelectionTest.map();
        assertTrue(map.compileRecipe(ConditionRecipeSelectionTest.recipe(map).conditionDaytime().build().getResult()));
        assertTrue(map.compileRecipe(map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(8).conditionHeight(0, 100).build().getResult()));
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        machine.environment.daytime = false;
        ConditionRuntimeLogic logic = new ConditionRuntimeLogic(machine, map);
        ItemStackHandler inputs = new ItemStackHandler(2);
        inputs.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        inputs.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        RecipeBuilder<?> filtered = logic.findAppendedParallelItemRecipe(map, inputs, new ItemStackHandler(2), 2, 32,
                machine);
        assertNotNull(filtered);
        assertEquals(1, filtered.getParallel());
        assertEquals(1, filtered.getRecipeConditions().clauseCount());
        assertEquals(0, logic.finalChecks);
        assertEquals(2,
                ParallelLogic.appendItemRecipes(map, inputs, new ItemStackHandler(2), 2, 32, machine).getParallel());
    }
}

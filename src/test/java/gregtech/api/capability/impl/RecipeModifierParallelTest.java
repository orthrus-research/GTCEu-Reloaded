package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.multiblock.ParallelLogicType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;
import gregtech.api.recipes.modifiers.RecipeModification;
import gregtech.api.recipes.modifiers.RecipeModifier;
import gregtech.api.recipes.modifiers.RecipeModifierCopies;
import gregtech.api.recipes.modifiers.RecipeModifierList;
import gregtech.api.recipes.properties.impl.ConditionProperty;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.api.capability.impl.RecipeModifierRuntimeTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierParallelTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void appendUsesAdjustedRatiosAndCachesRepeatedSourceAcrossSlots() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(4);
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 4));
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.builder().itemInputs(2, 0).itemOutputs(2, 0)
                .duration(2, 0).build());
        ModifierRuntimeLogic logic = append(machine, map, 3);
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, calls.get());
        assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty());
        assertEquals(2, machine.getImportItems().getStackInSlot(1).getCount());
        assertEquals(6, logic.itemOutputs.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(48, logic.prepared.getDuration());
        assertEquals(3, logic.getParallelRecipesPerformed());
        assertEquals(8, source.getDuration());
    }

    @Test
    void appendKeepsEarlierAdmissionWhenLaterSourceCancelsOverflowsOrHasIncompatibleMetadata() {
        for (String rejection : new String[] { "cancel", "overflow", "metadata" }) {
            RecipeMap<SimpleRecipeBuilder> map = map();
            Recipe first = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).dimension(0).build().getResult();
            SimpleRecipeBuilder secondBuilder = recipe(map).inputs(new ItemStack(Items.GOLD_INGOT)).clearOutputs()
                    .outputs(new ItemStack(Items.APPLE)).dimension(0);
            if (rejection.equals("metadata")) secondBuilder.dimension(1);
            Recipe second = secondBuilder.build().getResult();
            assertTrue(map.compileRecipe(first));
            assertTrue(map.compileRecipe(second));
            ModifierRuntimeMachine machine = machine(4);
            machine.environment.provider.setDimension(0); // DummyWorld intentionally defaults to Integer.MAX_VALUE.
            machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 4));
            AtomicInteger calls = new AtomicInteger();
            machine.modifiers = RecipeModifierList.of(RecipeModifier.named("test:partial", (context, view) -> {
                calls.incrementAndGet();
                if (view.itemOutputs().get(0).getItem() == Items.APPLE) {
                    if (rejection.equals("cancel")) return RecipeModification.cancel("test.cancel_later");
                    if (rejection.equals("overflow")) return RecipeModification.builder().itemOutputs(1,
                            Integer.MAX_VALUE).build();
                }
                return RecipeModification.builder().itemInputs(2, 0).itemOutputs(2, 0).build();
            }));
            ModifierRuntimeLogic logic = append(machine, map, 4);
            logic.previousRecipe = first;
            logic.trySearchNewRecipe();
            assertEquals(1, logic.getProgress(), rejection);
            assertEquals(2, calls.get(), rejection);
            assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty(), rejection);
            assertEquals(4, machine.getImportItems().getStackInSlot(1).getCount(), rejection);
            assertEquals(4, logic.itemOutputs.stream().mapToInt(ItemStack::getCount).sum(), rejection);
            assertEquals(2, logic.getParallelRecipesPerformed(), rejection);
            assertTrue(logic.itemOutputs.stream().allMatch(stack -> stack.getItem() == Items.GOLD_NUGGET));
            assertEquals(1, second.getOutputs().get(0).getCount());
        }
    }

    @Test
    void laterChanceOrDuplicateIdentityOutputsCannotEraseEarlierViableAppendWork() {
        for (boolean duplicateOverflow : new boolean[] { false, true }) {
            RecipeMap<SimpleRecipeBuilder> map = map();
            Recipe first = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
            SimpleRecipeBuilder secondBuilder = recipe(map).inputs(new ItemStack(Items.GOLD_INGOT)).clearOutputs();
            if (duplicateOverflow) secondBuilder.outputs(new ItemStack(Items.GOLD_NUGGET, Integer.MAX_VALUE),
                    new ItemStack(Items.GOLD_NUGGET, Integer.MAX_VALUE));
            else secondBuilder.chancedOutput(new ItemStack(Items.APPLE, 64), 5000, 0);
            Recipe second = secondBuilder.build().getResult();
            assertTrue(map.compileRecipe(first));
            assertTrue(map.compileRecipe(second));
            ModifierRuntimeMachine machine = machine(1);
            machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
            machine.getExportItems().setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 63));
            machine.getExportItems().setStackInSlot(1, new ItemStack(Blocks.BEDROCK, 64));
            machine.voidItems = duplicateOverflow; // Arithmetic bounds apply even when inventory capacity is ignored.
            AtomicInteger calls = new AtomicInteger();
            machine.modifiers = pipeline(calls, RecipeModification.IDENTITY);
            ModifierRuntimeLogic logic = append(machine, map, 2);
            logic.previousRecipe = first;
            logic.trySearchNewRecipe();
            assertEquals(1, logic.getProgress());
            assertEquals(1, logic.getParallelRecipesPerformed());
            assertEquals(2, calls.get());
            assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty());
            assertEquals(1, machine.getImportItems().getStackInSlot(1).getCount());
            assertEquals(1, logic.prepared.getOutputs().size());
            assertEquals(1, logic.prepared.getOutputs().get(0).getCount());
            assertTrue(logic.prepared.getChancedOutputs().getChancedEntries().isEmpty());
        }
    }

    @Test
    void laterFluidOutputSourceIsAnExplicitAppendBoundaryAndEarlierItemsStillStart() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe first = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        Recipe second = recipe(map).inputs(new ItemStack(Items.GOLD_INGOT))
                .fluidOutputs(new FluidStack(FluidRegistry.WATER, 10)).build().getResult();
        assertTrue(map.compileRecipe(first));
        assertTrue(map.compileRecipe(second));
        ModifierRuntimeMachine machine = machine(1);
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        machine.modifiers = pipeline(new AtomicInteger(), RecipeModification.IDENTITY);
        ModifierRuntimeLogic logic = append(machine, map, 2);
        logic.previousRecipe = first;
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, logic.getParallelRecipesPerformed());
        assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty());
        assertEquals(1, machine.getImportItems().getStackInSlot(1).getCount());
        assertTrue(logic.prepared.getFluidOutputs().isEmpty());
    }

    @Test
    void parallelRetainsFlatChanceLogicAndAndedConditionsAfterLegacySpeedDiscount() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).clearOutputs()
                .chancedOutput(new ItemStack(Items.APPLE), 5000, 5).chancedOutputLogic(ChancedOutputLogic.AND)
                .conditionHeight(60, 70).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(8);
        machine.modifiers = pipeline(new AtomicInteger(), RecipeModification.builder().itemInputs(2, 0)
                .conditions(RecipeConditionSet.EMPTY.add(RecipeConditions.daytime(false))).build());
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        logic.setParallelLimit(3);
        logic.setSpeedBonus(.5);
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(3, logic.prepared.getChancedOutputs().getChancedEntries().size());
        assertSame(ChancedOutputLogic.AND, logic.prepared.getChancedOutputs().getChancedOutputLogic());
        assertEquals(4, logic.prepared.getDuration());
        assertEquals(2,
                logic.prepared.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY).clauseCount());
        assertEquals(1, source.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY).clauseCount());
    }

    @Test
    void hugeConfiguredParallelIsBoundedBeforeLegacyChanceExpansion() {
        for (ParallelLogicType type : ParallelLogicType.values()) {
            RecipeMap<SimpleRecipeBuilder> map = map();
            Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).duration(1).clearOutputs()
                    .chancedOutput(new ItemStack(Items.APPLE), 1, 0)
                    .chancedOutput(new ItemStack(Items.BREAD), 1, 0).build().getResult();
            assertTrue(map.compileRecipe(source));
            ModifierRuntimeMachine machine = machine(Integer.MAX_VALUE);
            // Synthetic bulk provider: ordinary Forge slots intentionally cap extractItem at a vanilla stack.
            machine.bulkInputs = new ItemStackHandler(1) {

                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    ItemStack stored = getStackInSlot(slot);
                    if (amount <= 0 || stored.isEmpty()) return ItemStack.EMPTY;
                    int count = Math.min(amount, stored.getCount());
                    ItemStack result = stored.copy();
                    result.setCount(count);
                    if (!simulate) stored.shrink(count);
                    return result;
                }
            };
            machine.bulkInputs.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE));
            machine.voidItems = true;
            machine.modifiers = pipeline(new AtomicInteger(), RecipeModification.IDENTITY);
            ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
            logic.voltage = Integer.MAX_VALUE;
            logic.stored = 100000;
            logic.parallelType = type;
            logic.setParallelLimit(Integer.MAX_VALUE);
            logic.trySearchNewRecipe();
            int operations = RecipeModifierCopies.MAX_PREPARED_CHANCE_ENTRIES / 2;
            assertEquals(1, logic.getProgress(), type.name());
            assertEquals(operations, logic.getParallelRecipesPerformed());
            assertEquals(RecipeModifierCopies.MAX_PREPARED_CHANCE_ENTRIES,
                    logic.prepared.getChancedOutputs().getChancedEntries().size());
            assertEquals(Integer.MAX_VALUE - operations, machine.getImportItems().getStackInSlot(0).getCount());
            assertEquals(2, source.getChancedOutputs().getChancedEntries().size());
        }
    }

    private static ModifierRuntimeLogic append(ModifierRuntimeMachine machine, RecipeMap<?> map, int limit) {
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        logic.parallelType = ParallelLogicType.APPEND_ITEMS;
        logic.setParallelLimit(limit);
        return logic;
    }
}

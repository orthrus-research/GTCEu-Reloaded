package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.recipes.modifiers.RecipeModification;
import gregtech.api.recipes.modifiers.RecipeModifier;
import gregtech.api.recipes.modifiers.RecipeModifierList;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierRuntimeTest {

    private static int nextMap;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void discountsReduceActualConsumptionButDoNotBypassOriginalDiscoveryMinimums() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT, 2)).EUt(16).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(1);
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.builder().itemInputs(.5, 0).energy(.5, 0).build());
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        assertEquals(0, calls.get());
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        logic.voltage = 8;
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        assertEquals(0, calls.get());
        logic.voltage = 32;
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(8, logic.prepared.getEUt());
        assertEquals(1, calls.get());
        assertSame(source, logic.previousRecipe);
        assertEquals(2, source.getInputs().get(0).getAmount());
        assertEquals(16, source.getEUt());
    }

    @Test
    void increasedRequirementsAndCancelledCachedCandidatesRejectBeforeSelectedChecks() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        Recipe allowed = recipe(map).inputs(new ItemStack(Items.GOLD_INGOT)).clearOutputs()
                .outputs(new ItemStack(Items.APPLE)).build().getResult();
        assertTrue(map.compileRecipe(blocked));
        assertTrue(map.compileRecipe(allowed));
        ModifierRuntimeMachine machine = machine(2);
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = RecipeModifierList.of(RecipeModifier.named("test:select", (context, view) -> {
            calls.incrementAndGet();
            return view.itemOutputs().get(0).getItem() == Items.GOLD_NUGGET ?
                    RecipeModification.cancel("test.first_reason") :
                    RecipeModification.builder().duration(2, 0).build();
        }));
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        logic.previousRecipe = blocked;
        logic.trySearchNewRecipe();
        assertSame(allowed, logic.previousRecipe);
        assertEquals(2, calls.get(), "Each original is transformed once across cached selection and preparation");
        assertEquals(1, logic.finalChecks);
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(1, machine.getImportItems().getStackInSlot(1).getCount());
        ModifierRuntimeMachine increased = machine(1);
        increased.modifiers = pipeline(new AtomicInteger(), RecipeModification.builder().itemInputs(2, 0).build());
        ModifierRuntimeLogic rejected = new ModifierRuntimeLogic(increased, map);
        rejected.trySearchNewRecipe();
        assertEquals(0, rejected.finalChecks);
        assertEquals(0, rejected.getProgress());
        assertEquals(1, increased.getImportItems().getStackInSlot(0).getCount());
        assertEquals(RecipeFailureReason.INPUT_CHANGED, rejected.getRecipeFailure().getReason());
    }

    @Test
    void allRejectedCandidatesKeepFirstOriginalFailureProvenanceAndDiagnosticsDoNotInvokeCallbacks() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe first = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        assertTrue(map.compileRecipe(first));
        assertTrue(map.compileRecipe(recipe(map).inputs(new ItemStack(Items.GOLD_INGOT)).clearOutputs()
                .outputs(new ItemStack(Items.APPLE)).build().getResult()));
        ModifierRuntimeMachine machine = machine(2);
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = RecipeModifierList.of(RecipeModifier.named("test:provenance", (context, view) -> {
            calls.incrementAndGet();
            return RecipeModification.cancel(view.itemOutputs().get(0).getItem() == Items.GOLD_NUGGET ?
                    "test.first_reason" : "test.second_reason");
        }));
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        logic.previousRecipe = first;
        logic.trySearchNewRecipe();
        RecipeFailure failure = logic.getRecipeFailure();
        assertEquals(RecipeFailureReason.MODIFIER_CANCELLED, failure.getReason());
        assertSame(first, logic.previousRecipe);
        assertEquals("test.first_reason", failure.getConditionDescriptions().get(0).arguments().get(2));
        assertEquals(2, calls.get());
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        failure.write(packet);
        assertEquals(failure, RecipeFailure.read(packet));
        packet.release();
        for (int i = 0; i < 20; i++) {
            NBTTagCompound probe = new NBTTagCompound();
            logic.getRecipeFailure().writeToNBT(probe);
            assertEquals(failure, RecipeFailure.readFromNBT(probe));
            failure.format((key, arguments) -> key);
        }
        assertEquals(2, calls.get());
        assertEquals(0, logic.finalChecks);
    }

    @Test
    void transformedInputsAndOutputsControlParallelSizingBeforeAnythingIsConsumed() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(8);
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 60));
        machine.getExportItems().setStackInSlot(1, new ItemStack(Blocks.BEDROCK, 64));
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.builder().itemInputs(3, 0).itemOutputs(2, 0).build());
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        logic.setParallelLimit(4);
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(4, logic.itemOutputs.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(2, logic.getParallelRecipesPerformed());
        assertEquals(1, calls.get());
        assertEquals(1, source.getInputs().get(0).getAmount());
        assertEquals(1, source.getOutputs().get(0).getCount());
    }

    @Test
    void directPreparationGetsFreshAttemptsAfterFailureAndDisplayLookupNeverRunsModifiers() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(4);
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.cancel("test.cancel"));
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        assertSame(source, logic.findRecipe(32, machine.getImportItems(), machine.getImportFluids()));
        assertEquals(0, calls.get());
        assertFalse(logic.prepareRecipe(source));
        assertEquals(1, calls.get());
        assertEquals(0, logic.finalChecks, "Direct prepare retains its existing selected-check distinction");
        machine.modifiers = pipeline(calls, RecipeModification.builder().itemInputs(2, 0).build());
        assertTrue(logic.prepareRecipe(source));
        assertEquals(2, calls.get());
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void explicitEligibilityUsesAnEphemeralAttemptAndDoesNotSeedLaterStartup() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(2);
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.builder().duration(2, 0).build());
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        assertTrue(logic.evaluateRecipeEligibility(source).isEmpty());
        assertEquals(1, calls.get());
        assertFalse(logic.hasRecipeModifierAttempt());
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        machine.modifiers = pipeline(calls, RecipeModification.builder().duration(3, 0).build());
        logic.trySearchNewRecipe();
        assertEquals(2, calls.get());
        assertEquals(24, logic.prepared.getDuration());
    }

    @Test
    void nestedPreparationWithSameItemsButDifferentFluidsRejectsWithoutConsumption() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT))
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 10)).build().getResult();
        ModifierRuntimeMachine machine = machine(2);
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.IDENTITY);
        ModifierRuntimeLogic logic = new ModifierRuntimeLogic(machine, map);
        FluidTank first = new FluidTank(new FluidStack(FluidRegistry.WATER, 100), 1000);
        FluidTank second = new FluidTank(new FluidStack(FluidRegistry.WATER, 100), 1000);
        FluidTankList firstView = new FluidTankList(true, first), secondView = new FluidTankList(true, second);
        assertFalse(logic.withRecipeModifiers(machine.getImportItems(), firstView,
                () -> logic.prepareRecipe(source, machine.getImportItems(), secondView)));
        assertEquals(0, calls.get());
        assertEquals(0, logic.getProgress());
        assertEquals(2, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(100, first.getFluidAmount());
        assertEquals(100, second.getFluidAmount());
        assertFalse(logic.hasRecipeModifierAttempt());
        assertTrue(logic.prepareRecipe(source, machine.getImportItems(), secondView));
        assertEquals(1, calls.get());
        assertEquals(100, first.getFluidAmount());
        assertEquals(90, second.getFluidAmount());
    }

    @Test
    void preparedConditionsAndAdjustmentsSurviveHoldRestartAndPendingOutputWithoutCallbacks() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe source = recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).EUt(4).duration(6).build().getResult();
        assertTrue(map.compileRecipe(source));
        ModifierRuntimeMachine machine = machine(2);
        AtomicInteger calls = new AtomicInteger();
        machine.modifiers = pipeline(calls, RecipeModification.builder().energy(2, 0).duration(2, 0)
                .conditions(RecipeConditionSet.EMPTY.add(RecipeConditions.daytime(false))).build());
        ModifierRuntimeLogic first = new ModifierRuntimeLogic(machine, map);
        first.trySearchNewRecipe();
        machine.tick++;
        first.update();
        assertEquals(2, first.getProgress());
        machine.environment.daytime = false;
        for (int i = 0; i < 30; i++) {
            machine.tick++;
            first.update();
        }
        assertEquals(2, first.getProgress());
        assertTrue(first.isRecipeConditionPaused());
        assertEquals(1, calls.get());
        NBTTagCompound saved = first.serializeNBT();
        ModifierRuntimeMachine loadedMachine = machine(1);
        loadedMachine.environment.daytime = false;
        loadedMachine.modifiers = RecipeModifierList.of(RecipeModifier.named("test:changed", (context, view) -> {
            throw new AssertionError("Active/pending work must not invoke a changed provider");
        }));
        ModifierRuntimeLogic loaded = new ModifierRuntimeLogic(loadedMachine, map);
        loaded.stored = first.stored;
        loaded.deserializeNBT(saved);
        loadedMachine.tick++;
        loaded.update();
        assertEquals(2, loaded.getProgress());
        assertEquals(0, loaded.finalChecks);
        loadedMachine.environment.daytime = true;
        loadedMachine.acceptItems = false;
        for (int i = 0; i < 30 && !loaded.isAwaitingOutputs(); i++) {
            loadedMachine.tick++;
            loaded.update();
        }
        assertTrue(loaded.isAwaitingOutputs());
        assertEquals(2048 - 96, loaded.stored);
        loadedMachine.environment.daytime = false;
        loadedMachine.environment.forbidConditionReads = true;
        NBTTagCompound sampled = loaded.serializeNBT();
        for (int i = 0; i < 30; i++) {
            loadedMachine.tick++;
            loaded.update();
        }
        assertEquals(sampled.getTag("ItemOutputs"), loaded.serializeNBT().getTag("ItemOutputs"));
        loadedMachine.acceptItems = true;
        loadedMachine.tick++;
        loaded.update();
        loaded.update();
        assertEquals(1, loaded.completions);
        assertEquals(1, loadedMachine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(1, loadedMachine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(2048 - 96, loaded.stored);
        assertEquals(4, source.getEUt());
        assertEquals(6, source.getDuration());
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("d2_runtime_" + nextMap++, new SimpleRecipeBuilder())
                .itemInputs(4).itemOutputs(4).fluidInputs(1).fluidOutputs(1).build();
    }

    static SimpleRecipeBuilder recipe(RecipeMap<SimpleRecipeBuilder> map) {
        return map.recipeBuilder().outputs(new ItemStack(Items.GOLD_NUGGET)).EUt(1).duration(8);
    }

    static ModifierRuntimeMachine machine(int count) {
        ModifierRuntimeMachine machine = new ModifierRuntimeMachine();
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, count));
        return machine;
    }

    static RecipeModifierList pipeline(AtomicInteger calls, RecipeModification modification) {
        return RecipeModifierList.of(RecipeModifier.named("test:runtime", (context, view) -> {
            calls.incrementAndGet();
            return modification;
        }));
    }
}

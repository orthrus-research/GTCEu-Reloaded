package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.GTUtility;
import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class IdleRecipeSchedulingTest {

    private static int nextId;
    private static MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        if (previousCircuit == null) {
            // Bootstrap creates meta items but leaves subitem registration to Forge's normal item event.
            MetaItem<?> meta = MetaItems.ITEMS.get(0);
            MetaItems.INTEGRATED_CIRCUIT = meta.getItem("circuit.integrated");
            if (MetaItems.INTEGRATED_CIRCUIT == null) {
                MetaItems.INTEGRATED_CIRCUIT = meta.addItem(461, "circuit.integrated");
            }
        }
    }

    @AfterAll
    static void restoreCircuit() {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 7, 19 })
    void idleCallbacksKeepConditionPhaseWithoutRepeatingSearches(int phase) {
        IdleRecipeTestMachine sleeping = new IdleRecipeTestMachine(map());
        IdleRecipeTestMachine continuous = new IdleRecipeTestMachine(map());
        continuous.scheduling = false;
        for (int tick = phase; tick < phase + 100; tick++) {
            sleeping.tick(tick);
            continuous.tick(tick);
        }
        assertEquals(phase == 0 ? 5 : 6, sleeping.logic().callbacks);
        assertEquals(100, continuous.logic().callbacks);
        assertEquals(1, sleeping.logic().searches);
        assertEquals(1, continuous.logic().searches);
        assertEquals(5, sleeping.logic().conditions);
        assertEquals(continuous.logic().conditions, sleeping.logic().conditions);
        assertEquals(continuous.logic().serializeNBT(), sleeping.logic().serializeNBT());
    }

    @Test
    void itemFluidAndCircuitNotificationsWakeTheNextCallbackWithoutGateMutation() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 100)).circuitMeta(1));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.tick(1);
        assertFalse(machine.logic().shouldUpdateRecipeLogic());
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        int notifications = machine.getNotifiedItemInputList().size();
        for (int i = 0; i < 100; i++) assertTrue(machine.logic().shouldUpdateRecipeLogic());
        assertEquals(notifications, machine.getNotifiedItemInputList().size());
        assertEquals(1, machine.logic().searches);
        machine.tick(2);
        assertEquals(2, machine.logic().searches);
        machine.getImportFluids().fill(new FluidStack(FluidRegistry.WATER, 100), true);
        machine.tick(3);
        assertEquals(3, machine.logic().searches);
        machine.setGhostCircuitConfig(1);
        machine.tick(4);
        assertEquals(4, machine.logic().searches);
        assertEquals(1, machine.logic().getProgress());
        assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty());
        assertEquals(0, machine.getImportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void staleOutputNotificationsDoNotWakeNoInputLogicAndCoverInputDoes() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        machine.tick(1);
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.STICK));
        assertFalse(machine.getNotifiedItemOutputList().isEmpty());
        machine.tick(2);
        assertEquals(1, machine.logic().callbacks);
        machine.coverAction = () -> machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        machine.tick(3);
        assertEquals(1, machine.logic().callbacks);
        machine.coverAction = null;
        machine.tick(4);
        assertEquals(2, machine.logic().callbacks);
        assertEquals(2, machine.logic().searches);
    }

    @Test
    void explicitWakeCoalescesAndAnEventDuringUpdateSurvives() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        machine.tick(1);
        for (int i = 0; i < 100; i++) machine.logic().requestRecipeLogicWakeup();
        machine.logic().afterUpdate = machine.logic()::requestRecipeLogicWakeup;
        machine.tick(2);
        assertEquals(2, machine.logic().callbacks);
        machine.logic().afterUpdate = null;
        machine.tick(3);
        assertEquals(3, machine.logic().callbacks);
        machine.tick(4);
        assertEquals(3, machine.logic().callbacks);
        assertEquals(1, machine.logic().searches);
    }

    @Test
    void inputNotificationRaisedDuringTheCallbackRemainsAvailableForTheNextOne() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.logic().afterUpdate = () -> machine.getImportItems().setStackInSlot(0,
                new ItemStack(Items.IRON_INGOT));
        machine.tick(1);
        assertEquals(1, machine.logic().searches);
        assertFalse(machine.getNotifiedItemInputList().isEmpty());
        assertTrue(machine.logic().shouldUpdateRecipeLogic());
        machine.logic().afterUpdate = null;
        machine.tick(2);
        assertEquals(2, machine.logic().searches);
        assertEquals(1, machine.logic().getProgress());
    }

    @Test
    void disabledCleanupResumeLoadAndInvalidationWakeWithoutPersistentSleepState() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        machine.tick(1);
        machine.logic().wasActiveAndNeedsUpdate = true;
        machine.logic().setWorkingEnabled(false);
        machine.tick(2);
        assertFalse(machine.logic().wasActiveAndNeedsUpdate);
        for (int tick = 3; tick < 43; tick++) machine.tick(tick);
        assertEquals(2, machine.logic().callbacks);
        machine.logic().setWorkingEnabled(true);
        machine.tick(43);
        assertEquals(3, machine.logic().callbacks);
        NBTTagCompound saved = machine.logic().serializeNBT();
        machine.logic().requestRecipeLogicWakeup();
        assertEquals(saved, machine.logic().serializeNBT());
        machine.tick(44);
        machine.logic().deserializeNBT(saved);
        machine.tick(45);
        assertEquals(5, machine.logic().callbacks);
        machine.logic().invalidate();
        machine.tick(46);
        assertEquals(6, machine.logic().callbacks);
        assertEquals(2, machine.logic().searches);
        machine.onLoad();
        machine.tick(47);
        assertEquals(7, machine.logic().callbacks);
    }

    @Test
    void configurationWakeAndSynchronousRecheckDoNotSearchTwice() {
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map());
        machine.tick(1);
        machine.logic().setMaximumOverclockVoltage(32);
        machine.logic().setAllowOverclocking(false);
        machine.logic().setParallelLimit(1);
        machine.logic().setEUDiscount(1);
        machine.logic().setSpeedBonus(1);
        machine.tick(2);
        assertEquals(2, machine.logic().callbacks);
        assertEquals(1, machine.logic().searches);
        machine.logic().forceRecipeRecheck();
        assertEquals(2, machine.logic().searches);
        machine.tick(3);
        assertEquals(2, machine.logic().searches);
    }

    @Test
    void environmentalRetryKeepsItsOriginalDeadlineAndInputChangesBypassIt() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).cleanroom(CleanroomType.CLEANROOM));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.energy().setEnergyStored(1000);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        machine.tick(7);
        assertEquals(1, machine.logic().searches);
        RecipeFailureCleanroom cleanroom = new RecipeFailureCleanroom();
        cleanroom.clean = true;
        cleanroom.correctType = true;
        machine.setCleanroom(cleanroom);
        for (int tick = 8; tick < 27; tick++) machine.tick(tick);
        assertEquals(2, machine.logic().callbacks); // tick 20 remains aligned, but is before the search deadline.
        assertEquals(1, machine.logic().searches);
        machine.tick(27);
        assertEquals(1, machine.logic().getProgress());
        assertEquals(2, machine.logic().searches);

        IdleRecipeTestMachine changedInput = new IdleRecipeTestMachine(map);
        changedInput.energy().setEnergyStored(1000);
        changedInput.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        changedInput.tick(7);
        changedInput.setCleanroom(cleanroom);
        changedInput.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        changedInput.tick(8);
        assertEquals(1, changedInput.logic().getProgress());
    }

    @Test
    void powerOutputAndRunningStallsKeepContinuousCallbacksAndRecover() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        IdleRecipeTestMachine machine = new IdleRecipeTestMachine(map);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        for (int tick = 1; tick <= 5; tick++) machine.tick(tick);
        assertEquals(5, machine.logic().callbacks);
        assertEquals(RecipeFailureReason.NOT_ENOUGH_ENERGY, machine.logic().getRecipeFailure().getReason());
        machine.energy().setEnergyStored(1000);
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.STICK, 64));
        machine.getExportItems().setStackInSlot(1, new ItemStack(Items.STICK, 64));
        for (int tick = 6; tick <= 10; tick++) machine.tick(tick);
        assertEquals(10, machine.logic().callbacks);
        assertEquals(RecipeFailureReason.ITEM_OUTPUT_FULL, machine.logic().getRecipeFailure().getReason());
        machine.getExportItems().setStackInSlot(0, ItemStack.EMPTY);
        machine.tick(11);
        assertEquals(1, machine.logic().getProgress());
        machine.energy().setEnergyStored(0);
        for (int tick = 12; tick <= 16; tick++) machine.tick(tick);
        assertEquals(16, machine.logic().callbacks);
        assertEquals(1, machine.logic().getProgress());
        machine.energy().setEnergyStored(1000);
        machine.tick(17);
        assertEquals(2, machine.logic().getProgress());
    }

    @Test
    void ordinaryCoreClassesAreAdmittedButUnknownMachinesAndLogicStayContinuous() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        SimpleMachineMetaTileEntity machine = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("idle_admission"), map, null, 1, true);
        IdleRecipeTestMachine.attachHolder(machine);
        assertTrue(machine.supportsIdleRecipeScheduling());
        AbstractRecipeLogic logic = (AbstractRecipeLogic) machine.getMTETrait("RecipeMapWorkable");
        assertNotNull(logic);
        assertTrue(logic.supportsIdleRecipeScheduling());
        SimpleMachineMetaTileEntity unknown = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("idle_unknown"), map, null, 1, true) {};
        assertFalse(unknown.supportsIdleRecipeScheduling());
        RecipeLogicEnergy unknownLogic = new RecipeLogicEnergy(machine, map, () -> IEnergyContainer.DEFAULT) {};
        assertFalse(unknownLogic.supportsIdleRecipeScheduling());
        IdleRecipeTestMachine continuous = new IdleRecipeTestMachine(map);
        continuous.logic().scheduling = false;
        for (int tick = 1; tick <= 5; tick++) continuous.tick(tick);
        assertEquals(5, continuous.logic().callbacks);
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("idle_scheduling_" + nextId++, new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(2).fluidInputs(1).fluidOutputs(1).build();
    }

    static Recipe add(RecipeMap<SimpleRecipeBuilder> map, SimpleRecipeBuilder builder) {
        Recipe recipe = builder.outputs(new ItemStack(Items.GOLD_INGOT)).EUt(1).duration(8).build().getResult();
        assertTrue(map.compileRecipe(recipe));
        return recipe;
    }
}

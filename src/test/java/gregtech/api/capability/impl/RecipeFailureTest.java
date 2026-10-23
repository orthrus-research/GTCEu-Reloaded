package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFailureTest {

    private static int nextId;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void startupPowerUsesTheExistingEightTickThresholdAndClearsOnSuccess() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        logic.stored = 7;
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.NOT_ENOUGH_ENERGY);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(1, logic.finalChecks);
        assertEquals(1, logic.preparations);
        logic.stored = 8;
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(7, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(2, logic.finalChecks);
        assertEquals(2, logic.preparations);
    }

    @Test
    void itemAndFluidOutputFailuresComeFromTheActualOutputChecks() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .fluidOutputs(new FluidStack(FluidRegistry.WATER, 100)));
        RecipeFailureTestMachine machine = populated();
        machine.getExportItems().setStackInSlot(0, new ItemStack(Items.STICK, 64));
        machine.getExportItems().setStackInSlot(1, new ItemStack(Items.STICK, 64));
        machine.getExportFluids().fill(new FluidStack(FluidRegistry.LAVA, 1000), true);
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.ITEM_OUTPUT_FULL);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
        machine.getExportItems().setStackInSlot(0, ItemStack.EMPTY);
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.FLUID_OUTPUT_FULL);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
        machine.getExportFluids().drain(1000, true);
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.NONE);
        assertSame(recipe, logic.getPreviousRecipe());
        assertEquals(7, machine.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void genericFinalAndPreparationFailuresDoNotInventEnvironmentDetails() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(populated(), map);
        logic.finalAllowed = false;
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.REQUIREMENTS);
        assertEquals(1, logic.finalChecks);
        assertEquals(0, logic.preparations);
        logic.finalAllowed = true;
        logic.preparationAllowed = false;
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.SETUP);
        assertEquals(2, logic.finalChecks);
        assertEquals(1, logic.preparations);
    }

    @Test
    void changedInputsAreReportedAfterExactlyOneFinalCheck() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map) {

            @Override
            public boolean checkRecipe(Recipe recipe) {
                machine.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
                return super.checkRecipe(recipe);
            }
        };
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.INPUT_CHANGED);
        assertEquals(1, logic.finalChecks);
        assertEquals(1, logic.preparations);
    }

    @Test
    void aSuccessfulAlternativeDiscardsTheRejectedCandidatesReason() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .cleanroom(CleanroomType.CLEANROOM));
        Recipe allowed = add(map, map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT)));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        logic.previousRecipe = blocked;
        logic.trySearchNewRecipe();
        assertSame(allowed, logic.getPreviousRecipe());
        assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(7, machine.getImportItems().getStackInSlot(1).getCount());
        assertEquals(1, logic.finalChecks);
    }

    @Test
    void allBlockedCandidatesKeepTheRetainedReasonAndRecoverOnBoundedRetry() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe cleanroom = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .cleanroom(CleanroomType.CLEANROOM));
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT)).dimension(123456));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        logic.previousRecipe = cleanroom;
        logic.trySearchNewRecipe();
        assertSame(cleanroom, logic.getPreviousRecipe());
        assertReason(logic, RecipeFailureReason.CLEANROOM_MISSING);
        assertEquals(0, logic.finalChecks);
        RecipeFailureCleanroom provider = new RecipeFailureCleanroom();
        provider.clean = true;
        provider.correctType = true;
        machine.setCleanroom(provider);
        machine.tick = 19;
        assertFalse(logic.shouldSearchForRecipes());
        machine.tick = 20;
        assertTrue(logic.shouldSearchForRecipes());
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(1, logic.finalChecks);
    }

    @Test
    void cleanroomAndDimensionReasonsRespectTheExistingShortCircuitOrder() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .cleanroom(CleanroomType.CLEANROOM).dimension(123456));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        assertEquals(RecipeFailureReason.CLEANROOM_MISSING, logic.evaluateRecipeEligibility(recipe).getReason());
        RecipeFailureCleanroom provider = new RecipeFailureCleanroom();
        machine.setCleanroom(provider);
        assertEquals(RecipeFailureReason.CLEANROOM_DIRTY, logic.evaluateRecipeEligibility(recipe).getReason());
        assertEquals(0, provider.typeChecks);
        provider.clean = true;
        assertEquals(RecipeFailureReason.CLEANROOM_TYPE, logic.evaluateRecipeEligibility(recipe).getReason());
        provider.correctType = true;
        assertEquals(RecipeFailureReason.DIMENSION, logic.evaluateRecipeEligibility(recipe).getReason());
        int cleanChecks = provider.cleanChecks;
        int typeChecks = provider.typeChecks;
        for (int i = 0; i < 100; i++) assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(cleanChecks, provider.cleanChecks);
        assertEquals(typeChecks, provider.typeChecks);
        assertEquals(0, logic.finalChecks);
    }

    @Test
    void addonOverridesCanDiscardSuperFailuresWithoutPublishingThem() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).dimension(123456));
        AbstractRecipeLogic logic = new AbstractRecipeLogic(populated(), map) {

            @Override
            public boolean checkRecipe(Recipe recipe) {
                assertFalse(super.checkRecipe(recipe));
                return true;
            }
        };
        assertTrue(logic.isRecipeEligible(recipe), "Unknown legacy overrides remain selected-only");
        assertTrue(logic.evaluateRecipeCheck(recipe).isEmpty());
        assertReason(logic, RecipeFailureReason.NONE);
        assertFalse(logic.recordRecipeFailure(RecipeFailureReason.DIMENSION));
        assertReason(logic, RecipeFailureReason.NONE);
    }

    @Test
    void runningEnvironmentFailureDoesNotProbePowerAndRecoveryClearsStickyLegacyWarning() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .cleanroom(CleanroomType.CLEANROOM));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        logic.previousRecipe = recipe;
        logic.progressTime = 10;
        logic.maxProgressTime = 100;
        logic.recipeEUt = 1;
        logic.drawAllowed = false;
        machine.tick = 20;
        logic.update();
        assertReason(logic, RecipeFailureReason.CLEANROOM_MISSING);
        assertEquals(0, logic.simulatedDraws);
        assertEquals(8, logic.progressTime);
        assertTrue(logic.hasNotEnoughEnergy, "The existing flag remains compatible");
        RecipeFailureCleanroom provider = new RecipeFailureCleanroom();
        provider.clean = true;
        provider.correctType = true;
        machine.setCleanroom(provider);
        machine.tick = 40;
        logic.update();
        assertReason(logic, RecipeFailureReason.NOT_ENOUGH_ENERGY);
        assertEquals(1, logic.simulatedDraws);
        logic.drawAllowed = true;
        machine.tick++;
        logic.update();
        assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(2, logic.simulatedDraws);
        assertEquals(1, logic.actualDraws);
        assertTrue(logic.hasNotEnoughEnergy, "The old throughput-based flag must not drive the new reason");
    }

    @Test
    void generatorEnergyBlockageIsNotReportedAsMissingInputEnergy() {
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(populated(), map());
        logic.progressTime = 5;
        logic.maxProgressTime = 100;
        logic.recipeEUt = -1;
        logic.drawAllowed = false;
        logic.updateRecipeProgress();
        assertReason(logic, RecipeFailureReason.ENERGY_OUTPUT_FULL);
        assertEquals(5, logic.progressTime);
        assertFalse(logic.hasNotEnoughEnergy);
        assertEquals(1, logic.simulatedDraws);
        assertEquals(0, logic.actualDraws);
    }

    @Test
    void disablingInvalidationAndLoadCannotResurrectStaleReasons() {
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(populated(), map());
        NBTTagCompound baseline = logic.serializeNBT();
        logic.setRecipeFailure(RecipeFailureReason.ITEM_OUTPUT_FULL);
        NBTTagCompound saved = logic.serializeNBT();
        assertEquals(baseline, saved, "No diagnostic state belongs in persistent machine NBT");
        logic.setWorkingEnabled(false);
        assertReason(logic, RecipeFailureReason.DISABLED);
        logic.setWorkingEnabled(true);
        assertReason(logic, RecipeFailureReason.NONE);
        logic.setRecipeFailure(RecipeFailureReason.DIMENSION);
        logic.deserializeNBT(saved);
        assertReason(logic, RecipeFailureReason.NONE);
        logic.setRecipeFailure(RecipeFailureReason.SETUP);
        logic.invalidate();
        assertReason(logic, RecipeFailureReason.NONE);
    }

    @Test
    void replacingInputsReplacesTheOldEnvironmentReason() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).cleanroom(CleanroomType.CLEANROOM));
        RecipeFailureTestMachine machine = populated();
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(machine, map);
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.CLEANROOM_MISSING);
        machine.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.NO_RECIPE);
        assertEquals(0, logic.finalChecks);
    }

    @Test
    void anExceptionalAddonCheckCannotLeakItsCaptureScope() {
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(populated(), map());
        assertThrows(IllegalStateException.class, () -> logic.captureRecipeFailure(() -> {
            logic.recordRecipeFailure(RecipeFailureReason.DIMENSION);
            throw new IllegalStateException("addon check");
        }, RecipeFailureReason.REQUIREMENTS));
        assertFalse(logic.recordRecipeFailure(RecipeFailureReason.CLEANROOM_MISSING));
        assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(RecipeFailureReason.REQUIREMENTS,
                logic.captureRecipeFailure(() -> false, RecipeFailureReason.REQUIREMENTS).getReason());
    }

    @Test
    void absentOrTooHighVoltageRecipesUseAnHonestCombinedLookupReason() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe tooHigh = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.APPLE))
                .EUt(128).duration(100).build().getResult();
        assertTrue(map.compileRecipe(tooHigh));
        RecipeFailureTestLogic logic = new RecipeFailureTestLogic(populated(), map);
        logic.trySearchNewRecipe();
        assertReason(logic, RecipeFailureReason.NO_RECIPE);
        assertEquals(0, logic.finalChecks);
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("recipe_failure_" + nextId++, new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(2).fluidOutputs(1).build();
    }

    static Recipe add(RecipeMap<SimpleRecipeBuilder> map, SimpleRecipeBuilder builder) {
        Recipe recipe = builder.outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).build().getResult();
        assertTrue(map.compileRecipe(recipe));
        return recipe;
    }

    static RecipeFailureTestMachine populated() {
        RecipeFailureTestMachine machine = new RecipeFailureTestMachine();
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 8));
        return machine;
    }

    static void assertReason(AbstractRecipeLogic logic, RecipeFailureReason expected) {
        assertEquals(expected, logic.getRecipeFailure().getReason());
    }
}

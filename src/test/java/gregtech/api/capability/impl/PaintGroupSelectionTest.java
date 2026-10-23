package gregtech.api.capability.impl;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAssemblyLine;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaintGroupSelectionTest {

    static final int RED = 0xFF0000, BLUE = 0x0000FF;
    private static DualHatchTestItems items;
    private static int nextMap;

    @BeforeAll
    static void setup() throws Exception {
        items = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws Exception {
        items.close();
    }

    @Test
    void existingDecorativePaintKeepsLegacyMixingUntilExplicitlyEnabled() {
        for (boolean enabled : new boolean[] { false, true }) {
            RecipeMap<SimpleRecipeBuilder> map = map();
            compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT), new ItemStack(Items.IRON_INGOT)));
            PaintGroupTestPart red = input(RED, Items.WHEAT, 1), blue = input(BLUE, Items.IRON_INGOT, 1);
            PaintGroupTestController controller = new PaintGroupTestController(map, red, blue, output(BLUE));
            assertFalse(controller.isPaintGroupingEnabled());
            controller.setPaintGroupingEnabled(enabled);
            controller.getRecipeMapWorkable().trySearchNewRecipe();
            assertEquals(enabled ? 0 : 1, controller.getRecipeMapWorkable().getProgress());
            assertEquals(enabled ? 1 : 0, red.getImportItems().getStackInSlot(0).getCount());
            assertEquals(enabled ? 1 : 0, blue.getImportItems().getStackInSlot(0).getCount());
        }
    }

    @Test
    void unpaintedInputsAreSharedButOtherPaintedItemsAndPairedFluidsAreNot() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT), new ItemStack(Items.IRON_INGOT))
                .fluidInputs(water(100)));
        PaintGroupTestPart red = input(RED, Items.WHEAT, 1), blue = input(BLUE, Items.IRON_INGOT, 1);
        PaintGroupTestPart shared = input(-1, Items.IRON_INGOT, 1);
        blue.getImportFluids().fill(water(100), true);
        PaintGroupTestController controller = grouped(map, red, blue, shared, output(RED));
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        shared.getImportFluids().fill(water(100), true);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        assertTrue(red.getImportItems().getStackInSlot(0).isEmpty());
        assertTrue(shared.getImportItems().getStackInSlot(0).isEmpty());
        assertEquals(0, shared.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(1, blue.getImportItems().getStackInSlot(0).getCount());
        assertEquals(100, blue.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(RED, controller.getRecipeMapWorkable().getActiveOutputColor());
    }

    @Test
    void distinctStillUsesOneBusAndOnlyItsOwnPairedFluidsWithinAColor() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT), new ItemStack(Items.IRON_INGOT))
                .fluidInputs(water(100)));
        PaintGroupTestPart first = input(RED, Items.WHEAT, 1), second = input(RED, Items.IRON_INGOT, 1);
        second.getImportFluids().fill(water(100), true);
        PaintGroupTestController controller = grouped(map, first, second, output(RED));
        controller.setDistinct(true);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        first.getImportItems().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        first.getImportFluids().fill(water(100), true);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        assertEquals(100, second.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(1, second.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void fluidOnlyRecipesSelectAColorAndDoNotDoubleCountItsDualTank() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().fluidInputs(water(150)));
        PaintGroupTestPart red = input(RED, Items.WHEAT, 0), blue = input(BLUE, Items.WHEAT, 0);
        red.getImportFluids().fill(water(100), true);
        blue.getImportFluids().fill(water(100), true);
        PaintGroupTestController controller = grouped(map, red, blue, output(RED), output(BLUE));
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        red.getImportFluids().fill(water(50), true);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        assertEquals(0, red.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(100, blue.getImportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void rawPaintIdentityAndRepaintNotificationReopenAnIdleSearch() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT), new ItemStack(Items.IRON_INGOT)));
        PaintGroupTestPart red = input(RED, Items.WHEAT, 1), other = input(BLUE, Items.IRON_INGOT, 1);
        PaintGroupTestController controller = grouped(map, red, other, output(RED));
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(RecipeFailureReason.NO_RECIPE, controller.getRecipeMapWorkable().getRecipeFailure().getReason());
        other.setPaintingColor(RED);
        assertTrue(controller.getRecipeMapWorkable().shouldSearchForRecipes());
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        PaintGroupTestPart unpainted = input(-1, Items.WHEAT, 0), white = input(0xFFFFFF, Items.WHEAT, 0);
        PaintGroupTestController whiteController = grouped(map(), unpainted, white);
        assertEquals(-1, unpainted.getPaintingColor());
        assertEquals(2, whiteController.getRecipeInputViews().getRecipeInputViews().size(),
                "Default casing/render paint must not collapse unpainted and explicitly white groups");
    }

    @Test
    void ambiguousOrDetachedOwnershipNeverBecomesSharedEvenWhenVoidingIsEnabled() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart input = input(RED, Items.WHEAT, 1);
        PaintGroupTestController controller = grouped(map, input, output(RED));
        controller.voidItems = controller.voidFluids = true;
        controller.forgetOwner(input.getRecipeHandler());
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(RecipeFailureReason.INPUT_GROUP_UNAVAILABLE,
                controller.getRecipeMapWorkable().getRecipeFailure().getReason());
        assertEquals(1, input.getImportItems().getStackInSlot(0).getCount());
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
    }

    @Test
    void parallelPreparationConsumesOnlyTheSelectedColorsLiveView() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart red = input(RED, Items.WHEAT, 3), blue = input(BLUE, Items.WHEAT, 5);
        PaintGroupTestController controller = grouped(map, red, blue, output(RED), output(BLUE));
        controller.getRecipeMapWorkable().setParallelLimit(4);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        assertTrue(red.getImportItems().getStackInSlot(0).isEmpty());
        assertEquals(5, blue.getImportItems().getStackInSlot(0).getCount());
        assertEquals(3, controller.getRecipeMapWorkable().itemOutputs.get(0).getCount());
    }

    @Test
    void distinctViewIncludesOrdinaryUnpaintedFluidButNotAnotherDualsPrivateFluid() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT))
                .fluidInputs(water(100), new FluidStack(FluidRegistry.LAVA, 30)));
        PaintGroupTestPart red = input(RED, Items.WHEAT, 1), other = input(RED, Items.IRON_INGOT, 1);
        PaintGroupTestFluidPart shared = new PaintGroupTestFluidPart(-1);
        shared.getImportFluids().fill(new FluidStack(FluidRegistry.LAVA, 30), true);
        other.getImportFluids().fill(water(100), true);
        PaintGroupTestController controller = grouped(map, red, other, shared, output(RED));
        controller.setDistinct(true);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        red.getImportFluids().fill(water(100), true);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        assertEquals(0, red.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(0, shared.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(100, other.getImportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void modeChangeDuringLegacyStartCheckRejectsTheStaleViewBeforePreparationOrConsumption() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1);
        PaintGroupTestController controller = grouped(map, source, output(RED));
        controller.beforeStart = () -> controller.setPaintGroupingEnabled(false);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        assertEquals(1, source.getImportItems().getStackInSlot(0).getCount());
        assertEquals(RecipeFailureReason.INPUT_CHANGED,
                controller.getRecipeMapWorkable().getRecipeFailure().getReason());
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, controller.getRecipeMapWorkable().getProgress());
        assertEquals(-3, controller.getRecipeMapWorkable().getActiveOutputColor());
    }

    @Test
    void cachedNowIneligibleRecipeFallsBackWithinTheSameColorBeforeSelectedSideEffects() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe daytime = compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).conditionDaytime());
        Recipe alternative = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE)).duration(2).EUt(1).build().getResult();
        assertTrue(map.compileRecipe(alternative));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 2);
        PaintGroupTestController controller = grouped(map, source, output(RED));
        ConditionRuntimeWorld world = new ConditionRuntimeWorld();
        controller.environment = world;
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertSame(daytime, logic.previousRecipe);
        logic.tryCompleteRecipe();
        source.getImportItems().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        world.daytime = false;
        logic.trySearchNewRecipe();
        assertSame(alternative, logic.previousRecipe);
        assertEquals(1, logic.getProgress());
        assertEquals(1, source.getImportItems().getStackInSlot(0).getCount());
        assertTrue(source.getImportItems().getStackInSlot(1).isEmpty());
        assertEquals(Items.APPLE, logic.itemOutputs.get(0).getItem());
        assertEquals(RED, logic.getActiveOutputColor());
    }

    @Test
    void exactCoreAdmissionDoesNotEnableOrderedMachinesOrAddonSubclasses() {
        assertTrue(PaintGroupSupport.supports(MetaTileEntityElectricBlastFurnace.class));
        assertFalse(PaintGroupSupport.supports(MetaTileEntityAssemblyLine.class));
        assertFalse(PaintGroupSupport.supports(PaintGroupTestController.class));
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("paint_group_" + nextMap++, new SimpleRecipeBuilder())
                .itemInputs(3).itemOutputs(2).fluidInputs(3).fluidOutputs(2).build();
    }

    static Recipe compile(SimpleRecipeBuilder builder) {
        Recipe recipe = builder.outputs(new ItemStack(Items.GOLD_INGOT)).duration(2).EUt(1).build().getResult();
        assertTrue(recipe.getRecipeCategory().getRecipeMap().compileRecipe(recipe));
        return recipe;
    }

    static PaintGroupTestPart input(int color, net.minecraft.item.Item item, int count) {
        PaintGroupTestPart part = new PaintGroupTestPart(false, color);
        if (count > 0) part.getImportItems().setStackInSlot(0, new ItemStack(item, count));
        return part;
    }

    static PaintGroupTestPart output(int color) {
        return new PaintGroupTestPart(true, color);
    }

    static FluidStack water(int amount) {
        return new FluidStack(FluidRegistry.WATER, amount);
    }

    static PaintGroupTestController grouped(RecipeMap<?> map, PaintGroupTestPart... parts) {
        PaintGroupTestController controller = new PaintGroupTestController(map, parts);
        controller.setPaintGroupingEnabled(true);
        return controller;
    }
}

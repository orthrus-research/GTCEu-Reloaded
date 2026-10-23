package gregtech.api.capability.impl;

import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.capability.impl.PaintGroupSelectionTest.*;
import static gregtech.common.covers.filter.CompositeFilterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PaintGroupOutputTest {

    private static DualHatchTestItems items;

    @BeforeAll
    static void setup() throws Exception {
        items = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws Exception {
        items.close();
    }

    @Test
    void preStartCapacityUsesTheCandidateRouteAndNeverConsumesAgainstAnotherColorsSpace() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1), red = output(RED), blue = output(BLUE);
        red.getItemFilter().setFilterStack(itemLeaf(new ItemStack(Items.DIAMOND)));
        PaintGroupTestController controller = grouped(map, source, red, blue);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        assertEquals(1, source.getImportItems().getStackInSlot(0).getCount());
        assertEquals(RecipeFailureReason.ITEM_OUTPUT_FULL,
                controller.getRecipeMapWorkable().getRecipeFailure().getReason());
        assertTrue(blue.getExportItems().getStackInSlot(0).isEmpty());
    }

    @Test
    void repaintAndModeChangesCannotRerouteSampledProductsOrChargeThePendingWait() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).fluidOutputs(water(100)));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1), target = output(RED), blue = output(BLUE);
        PaintGroupTestController controller = grouped(map, source, target, blue);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        target.setPaintingColor(BLUE);
        source.setPaintingColor(BLUE);
        controller.setPaintGroupingEnabled(false);
        for (int i = 0; i < 4; i++) logic.updateWorkable();
        assertTrue(logic.isAwaitingOutputs());
        assertFalse(logic.isActive());
        assertEquals(RED, logic.getActiveOutputColor());
        source.setPaintingColor(-1);
        controller.setPaintGroupingEnabled(true);
        controller.setPaintGroupingEnabled(false);
        assertEquals(RED, logic.getActiveOutputColor(),
                "Edits made during the completed-output wait cannot retarget it");
        long energy = controller.getEnergyContainer().getEnergyStored();
        for (int i = 0; i < 30; i++) logic.updateWorkable();
        assertEquals(energy, controller.getEnergyContainer().getEnergyStored());
        assertTrue(target.getExportItems().getStackInSlot(0).isEmpty());
        assertTrue(blue.getExportItems().getStackInSlot(0).isEmpty());
        target.setPaintingColor(RED);
        logic.setWorkingEnabled(false);
        logic.updateWorkable();
        assertTrue(logic.isAwaitingOutputs());
        logic.setWorkingEnabled(true);
        logic.updateWorkable();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(1, target.getExportItems().getStackInSlot(0).getCount());
        assertEquals(100, target.getExportFluids().getTankAt(0).getFluidAmount());
        for (int i = 0; i < 30; i++) logic.updateWorkable();
        assertEquals(1, target.getExportItems().getStackInSlot(0).getCount());
        assertEquals(100, target.getExportFluids().getTankAt(0).getFluidAmount());
        assertEquals(energy, controller.getEnergyContainer().getEnergyStored());
    }

    @Test
    void pendingReloadKeepsPaintRouteDespiteAChangedControllerModeAndNewHandlerIdentities() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1), target = output(RED);
        PaintGroupTestController controller = grouped(map, source, target);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        target.setPaintingColor(BLUE);
        logic.tryCompleteRecipe();
        logic.setWorkingEnabled(false);
        NBTTagCompound saved = logic.serializeNBT();
        PaintGroupTestPart newTarget = output(BLUE);
        PaintGroupTestController loaded = new PaintGroupTestController(map, newTarget);
        MultiblockRecipeLogic resumed = loaded.getRecipeMapWorkable();
        resumed.deserializeNBT(saved);
        assertFalse(loaded.isPaintGroupingEnabled());
        assertTrue(resumed.isAwaitingOutputs());
        assertFalse(resumed.isActive());
        assertEquals(RED, resumed.getActiveOutputColor());
        resumed.setWorkingEnabled(true);
        resumed.updateWorkable();
        assertTrue(resumed.isAwaitingOutputs());
        assertTrue(newTarget.getExportItems().getStackInSlot(0).isEmpty());
        newTarget.setPaintingColor(-1);
        resumed.updateWorkable();
        assertFalse(resumed.isAwaitingOutputs());
        assertEquals(1, newTarget.getExportItems().getStackInSlot(0).getCount());
        assertFalse(resumed.serializeNBT().hasKey("OutputRoute"));
    }

    @Test
    void missingOldRouteCompletesWithLegacyRoutingButMalformedPresentRouteBlocksEvenVoiding() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1), target = output(RED);
        PaintGroupTestController original = grouped(map, source, target);
        original.getRecipeMapWorkable().trySearchNewRecipe();
        target.setPaintingColor(BLUE);
        original.getRecipeMapWorkable().tryCompleteRecipe();
        NBTTagCompound saved = original.getRecipeMapWorkable().serializeNBT();
        for (boolean malformed : new boolean[] { false, true }) {
            NBTTagCompound tag = saved.copy();
            if (malformed) tag.setString("OutputRoute", "invalid");
            else tag.removeTag("OutputRoute");
            PaintGroupTestPart blue = output(BLUE);
            PaintGroupTestController loaded = grouped(map, blue);
            loaded.voidItems = loaded.voidFluids = malformed;
            loaded.getRecipeMapWorkable().deserializeNBT(tag);
            loaded.getRecipeMapWorkable().updateWorkable();
            assertEquals(malformed, loaded.getRecipeMapWorkable().isAwaitingOutputs());
            assertEquals(malformed ? 0 : 1, blue.getExportItems().getStackInSlot(0).getCount());
            if (malformed) {
                assertEquals(RecipeFailureReason.OUTPUT_GROUP_UNAVAILABLE,
                        loaded.getRecipeMapWorkable().getRecipeFailure().getReason());
                assertEquals(1, loaded.getRecipeMapWorkable().itemOutputs.get(0).getCount());
            }
        }
    }

    @Test
    void validRoutesRespectCategoryVoidingWithoutSpillingIntoAnotherColor() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).fluidOutputs(water(100)));
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1), target = output(RED), blue = output(BLUE);
        PaintGroupTestController controller = grouped(map, source, target, blue);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        target.getItemFilter().setFilterStack(itemLeaf(new ItemStack(Items.DIAMOND)));
        target.getFluidFilter().setFilterStack(fluidLeaf(new FluidStack(FluidRegistry.LAVA, 1)));
        controller.voidItems = true;
        logic.tryCompleteRecipe();
        assertTrue(logic.isAwaitingOutputs());
        assertEquals(RecipeFailureReason.FLUID_OUTPUT_FULL, logic.getRecipeFailure().getReason());
        controller.voidFluids = true;
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertTrue(target.getExportItems().getStackInSlot(0).isEmpty());
        assertEquals(0, target.getExportFluids().getTankAt(0).getFluidAmount());
        assertTrue(blue.getExportItems().getStackInSlot(0).isEmpty());
        assertEquals(0, blue.getExportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    void conditionHoldAndReloadRetainTheStartedRouteUntilPendingOutputsAreReleased() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).conditionDaytime());
        PaintGroupTestPart source = input(RED, Items.WHEAT, 1), target = output(RED);
        PaintGroupTestController controller = grouped(map, source, target);
        ConditionRuntimeWorld world = new ConditionRuntimeWorld();
        controller.environment = world;
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        world.daytime = false;
        long energy = controller.getEnergyContainer().getEnergyStored();
        for (int i = 0; i < 20; i++) logic.updateWorkable();
        assertTrue(logic.isRecipeConditionPaused());
        assertEquals(1, logic.getProgress());
        assertEquals(energy, controller.getEnergyContainer().getEnergyStored());
        assertEquals(RED, logic.getActiveOutputColor());
        source.setPaintingColor(BLUE);
        controller.setPaintGroupingEnabled(false);
        logic.updateWorkable();
        assertTrue(logic.isRecipeConditionPaused());
        assertEquals(1, logic.getProgress());
        assertEquals(energy, controller.getEnergyContainer().getEnergyStored());
        assertEquals(RED, logic.getActiveOutputColor(),
                "Input paint and mode edits during a condition hold affect only later starts");
        NBTTagCompound saved = logic.serializeNBT();
        PaintGroupTestPart replacement = output(BLUE);
        PaintGroupTestController loaded = new PaintGroupTestController(map, replacement);
        loaded.environment = world;
        MultiblockRecipeLogic resumed = loaded.getRecipeMapWorkable();
        resumed.deserializeNBT(saved);
        resumed.updateWorkable();
        assertEquals(1, resumed.getProgress());
        assertEquals(RED, resumed.getActiveOutputColor());
        assertTrue(resumed.isRecipeConditionPaused());
        world.daytime = true;
        for (int i = 0; i < 4; i++) resumed.updateWorkable();
        assertTrue(resumed.isAwaitingOutputs());
        world.forbidConditionReads = true;
        replacement.setPaintingColor(RED);
        resumed.updateWorkable();
        assertFalse(resumed.isAwaitingOutputs());
        assertEquals(1, replacement.getExportItems().getStackInSlot(0).getCount());
        assertFalse(resumed.serializeNBT().hasKey("OutputRoute"));
    }

    @Test
    void routeCodecIsTypedBoundedAndRoundtripsEveryValidMode() {
        for (RecipeOutputRoute route : new RecipeOutputRoute[] { RecipeOutputRoute.LEGACY,
                RecipeOutputRoute.painted(-1), RecipeOutputRoute.painted(0), RecipeOutputRoute.painted(0xFFFFFF) }) {
            RecipeOutputRoute copy = RecipeOutputRoute.deserialize(route.serialize());
            assertTrue(copy.valid);
            assertEquals(route.color, copy.color);
            assertEquals(route.painted, copy.painted);
        }
        for (String key : new String[] { "Version", "Painted", "Color" }) {
            NBTTagCompound malformed = RecipeOutputRoute.painted(RED).serialize();
            malformed.setString(key, "1");
            assertFalse(RecipeOutputRoute.deserialize(malformed).valid);
        }
        NBTTagCompound malformed = RecipeOutputRoute.painted(RED).serialize();
        malformed.setInteger("Color", -2);
        assertFalse(RecipeOutputRoute.deserialize(malformed).valid);
        malformed.setInteger("Color", 0x1000000);
        assertFalse(RecipeOutputRoute.deserialize(malformed).valid);
        malformed = RecipeOutputRoute.LEGACY.serialize();
        malformed.setInteger("Color", RED);
        assertFalse(RecipeOutputRoute.deserialize(malformed).valid);
    }
}

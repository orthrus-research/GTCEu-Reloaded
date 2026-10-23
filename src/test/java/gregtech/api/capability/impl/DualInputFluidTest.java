package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DualInputFluidTest {

    private static DualHatchTestItems items;
    private static int nextMap;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        items = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        items.close();
    }

    @Test
    void sharedAndDistinctViewsDeduplicateNestedWrappersWithoutExposingOtherDualTanks() {
        DualHatchTestPart first = part(), second = part();
        first.getImportFluids().fill(water(100), true);
        second.getImportFluids().fill(new FluidStack(FluidRegistry.LAVA, 200), true);
        FluidTank shared = new FluidTank(1000);
        shared.fill(water(50), true);
        List<IFluidTank> fluidAbilities = Arrays.asList(shared,
                new FluidTankList(true, first.getRecipeHandler().getTankAt(0)).getTankAt(0),
                second.getImportFluids().getTankAt(0));
        List<DualHandler> duals = Arrays.asList(first.getRecipeHandler(), second.getRecipeHandler());
        List<IFluidTank> onlyShared = MultiblockFluidInputs.shared(fluidAbilities, duals);
        assertEquals(Collections.singletonList(shared), onlyShared);
        FluidTankList base = new FluidTankList(true, onlyShared);
        IMultipleTankHandler all = MultiblockFluidInputs.extend(base, duals);
        assertEquals(5, all.getTanks());
        IMultipleTankHandler selected = MultiblockFluidInputs.extend(base,
                Collections.singletonList(first.getRecipeHandler()));
        assertEquals(3, selected.getTanks());
        assertNull(selected.drain(new FluidStack(FluidRegistry.LAVA, 1), false));
        assertEquals(150, selected.drain(water(1000), false).amount);
        assertEquals(150, selected.drain(water(1000), true).amount);
        assertEquals(0, shared.getFluidAmount());
        assertEquals(0, first.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(200, second.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(5, MultiblockFluidInputs.extend(all, duals).getTanks());
    }

    @Test
    void assembledViewRetainsTankOwnerFillPolicyAndNotifiesTheOutermostDualIdentity() {
        FluidTank one = new FluidTank(10), two = new FluidTank(10);
        DualHandler inner = new DualHandler(new ItemStackHandler(1), new FluidTankList(false, one, two), false);
        DualHandler outer = new DualHandler(new ItemStackHandler(1), inner, false);
        RecipeFailureTestMachine receiver = new RecipeFailureTestMachine();
        outer.addNotifiableMetaTileEntity(receiver);
        IMultipleTankHandler combined = MultiblockFluidInputs.extend(new FluidTankList(true),
                Collections.singletonList(outer));
        assertFalse(combined.getTankAt(0).allowSameFluidFill());
        assertEquals(10, combined.fill(water(20), false));
        assertEquals(0, one.getFluidAmount());
        assertTrue(receiver.getNotifiedItemInputList().isEmpty());
        assertEquals(10, combined.fill(water(20), true));
        assertEquals(10, one.getFluidAmount());
        assertEquals(0, two.getFluidAmount());
        assertEquals(Collections.singletonList(outer), receiver.getNotifiedItemInputList());
    }

    @Test
    void actualControllerModesSeparateFluidsAndRecipesCannotConsumeOtherDualStock() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = recipe(map, false);
        DualHatchTestPart first = part(), second = part();
        first.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        second.getImportFluids().fill(water(100), true);
        FluidTank shared = new FluidTank(1000);
        shared.fill(new FluidStack(FluidRegistry.LAVA, 30), true);
        DualInputTestController controller = new DualInputTestController(map, shared, first, second);
        controller.setDistinct(true);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        assertEquals(1, controller.getInputFluidInventory().getTanks());
        assertFalse(recipe.matches(false, first.getRecipeHandler(), logic.getInputTank(first.getRecipeHandler())));
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        assertEquals(2, first.getImportItems().getStackInSlot(0).getCount());
        assertEquals(100, second.getImportFluids().getTankAt(0).getFluidAmount());
        controller.setDistinct(false);
        assertEquals(5, controller.getInputFluidInventory().getTanks());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, first.getImportItems().getStackInSlot(0).getCount());
        assertEquals(0, second.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(0, shared.getFluidAmount());
    }

    @Test
    void fluidNotificationReopensTheActualInvalidatedDistinctInputAndConsumesExactlyOnce() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        recipe(map, false);
        DualHatchTestPart first = part(), second = part();
        first.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        FluidTank shared = new FluidTank(1000);
        shared.fill(new FluidStack(FluidRegistry.LAVA, 30), true);
        DualInputTestController controller = new DualInputTestController(map, shared, first, second);
        controller.setDistinct(true);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        controller.getNotifiedItemInputList().clear();
        controller.getNotifiedFluidInputList().clear();
        assertFalse(logic.canWorkWithInputs());
        first.getImportFluids().fill(water(100), false);
        assertFalse(logic.canWorkWithInputs());
        first.getImportFluids().fill(water(100), true);
        assertTrue(logic.canWorkWithInputs());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, first.getImportItems().getStackInSlot(0).getCount());
        assertEquals(0, first.getImportFluids().getTankAt(0).getFluidAmount());
        assertEquals(0, shared.getFluidAmount());
        assertTrue(second.getImportItems().getStackInSlot(0).isEmpty());
    }

    @Test
    void circuitAndPhysicalItemEditsWakeDistinctRetryWithoutMaterialCircuitConsumption() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        recipe(map, true);
        DualHatchTestPart part = part();
        part.getImportFluids().fill(water(100), true);
        FluidTank shared = new FluidTank(1000);
        shared.fill(new FluidStack(FluidRegistry.LAVA, 30), true);
        DualInputTestController controller = new DualInputTestController(map, shared, part);
        controller.setDistinct(true);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        controller.getNotifiedItemInputList().clear();
        controller.getNotifiedFluidInputList().clear();
        assertFalse(logic.canWorkWithInputs());
        part.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        assertTrue(logic.canWorkWithInputs());
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        controller.getNotifiedItemInputList().clear();
        controller.getNotifiedFluidInputList().clear();
        assertFalse(logic.canWorkWithInputs());
        part.setGhostCircuitConfig(4);
        assertTrue(logic.canWorkWithInputs());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, part.getImportItems().getStackInSlot(0).getCount());
        assertFalse(part.getRecipeHandler().getStackInSlot(4).isEmpty());
        assertEquals(4, part.getItemInventory().getSlots());
    }

    @Test
    void ordinaryItemAndFluidHandlersKeepTheirOrderAndFillPolicy() {
        FluidTank first = new FluidTank(100), second = new FluidTank(200);
        FluidTankList fluids = new FluidTankList(false, first, second);
        var shared = MultiblockFluidInputs.shared(fluids.getFluidTanks(),
                Collections.singletonList(new ItemStackHandler(1)));
        assertEquals(fluids.getFluidTanks(), shared);
        IMultipleTankHandler extended = MultiblockFluidInputs.extend(fluids,
                Collections.singletonList(new ItemStackHandler(1)));
        assertSame(fluids.getTankAt(0), extended.getTankAt(0));
        assertSame(fluids.getTankAt(1), extended.getTankAt(1));
        assertFalse(extended.allowSameFluidFill());
    }

    private static DualHatchTestPart part() {
        return new DualHatchTestPart(GTValues.LuV, false);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(FluidRegistry.WATER, amount);
    }

    private static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("dual_input_" + nextMap++, new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(1).fluidInputs(2).build();
    }

    private static Recipe recipe(RecipeMap<SimpleRecipeBuilder> map, boolean circuit) {
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .fluidInputs(water(100), new FluidStack(FluidRegistry.LAVA, 30))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(20).EUt(1);
        if (circuit) builder.circuitMeta(4);
        Recipe recipe = builder.build().getResult();
        assertTrue(map.compileRecipe(recipe));
        return recipe;
    }
}

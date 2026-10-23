package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.recipes.modifiers.RecipeModification;
import gregtech.common.metatileentities.multi.electric.generator.LargeTurbineWorkableHandler;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierSpecialTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void turbineIdleFuelGettersNeverConsultProviderAndStartupUsesAdjustedFuelParallel() throws Exception {
        RecipeMap<SimpleRecipeBuilder> map = RecipeModifierRuntimeTest.map();
        Recipe source = fuelRecipe(map, 10);
        assertTrue(map.compileRecipe(source));
        ModifierTurbineController controller = turbine(map);
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierRuntimeTest.pipeline(calls,
                RecipeModification.builder().energy(2, 0).fluidInputs(2, 0).build());
        LargeTurbineWorkableHandler logic = (LargeTurbineWorkableHandler) controller.getRecipeMapWorkable();
        for (int i = 0; i < 40; i++) {
            assertNotNull(logic.getRecipeFluidInputInfo());
            assertNotNull(logic.getInputFluidStack());
            logic.getPreviousRecipeDuration();
            logic.getRecipeFailure();
        }
        assertEquals(0, calls.get());
        assertEquals(0, controller.providerReads);
        assertEquals(1000, controller.fuel.getFluidAmount());
        ((MultiblockRecipeLogic) logic).trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, calls.get());
        assertEquals(992, controller.fuel.getFluidAmount(), "ceil(64 / 20) operations each consume two mB");
        assertEquals(16, excess(logic));
        assertEquals(4, ((AbstractRecipeLogic) logic).itemOutputs.get(0).getCount());
        assertSame(source, ((AbstractRecipeLogic) logic).previousRecipe);
        assertEquals(10, source.getEUt());
        assertEquals(1, source.getFluidInputs().get(0).getAmount());
        assertEquals(1, source.getOutputs().get(0).getCount());
    }

    @Test
    void turbineRejectedOutputRestoresCreditAndCancellationLeavesFuelAndCreditUntouched() throws Exception {
        RecipeMap<SimpleRecipeBuilder> map = RecipeModifierRuntimeTest.map();
        Recipe source = fuelRecipe(map, 10);
        ModifierTurbineController controller = turbine(map);
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierRuntimeTest.pipeline(calls,
                RecipeModification.builder().energy(2, 0).build());
        controller.products.setStackInSlot(0, new ItemStack(Blocks.BEDROCK, 64));
        LargeTurbineWorkableHandler logic = (LargeTurbineWorkableHandler) controller.getRecipeMapWorkable();
        assertFalse(logic.prepareRecipe(source));
        assertEquals(0, excess(logic));
        assertEquals(1000, controller.fuel.getFluidAmount());
        controller.products.setStackInSlot(0, ItemStack.EMPTY);
        assertTrue(logic.prepareRecipe(source));
        assertEquals(16, excess(logic));
        assertEquals(996, controller.fuel.getFluidAmount());
        ((AbstractRecipeLogic) logic).tryCompleteRecipe();
        controller.modifiers = RecipeModifierRuntimeTest.pipeline(calls, RecipeModification.cancel("test.no_fuel"));
        assertFalse(logic.prepareRecipe(source));
        assertEquals(16, excess(logic));
        assertEquals(996, controller.fuel.getFluidAmount());
        assertEquals(3, calls.get());
    }

    @Test
    void turbineExistingExcessCreditCanPrepareAnOwnedZeroFuelCycle() throws Exception {
        RecipeMap<SimpleRecipeBuilder> map = RecipeModifierRuntimeTest.map();
        Recipe source = fuelRecipe(map, 32);
        ModifierTurbineController controller = turbine(map);
        controller.efficiency = 300;
        controller.modifiers = RecipeModifierRuntimeTest.pipeline(new AtomicInteger(), RecipeModification.IDENTITY);
        LargeTurbineWorkableHandler logic = (LargeTurbineWorkableHandler) controller.getRecipeMapWorkable();
        for (int i = 0; i < 2; i++) {
            assertTrue(logic.prepareRecipe(source));
            ((AbstractRecipeLogic) logic).tryCompleteRecipe();
        }
        assertEquals(64, excess(logic));
        assertEquals(998, controller.fuel.getFluidAmount());
        assertTrue(logic.prepareRecipe(source));
        assertEquals(0, excess(logic));
        assertEquals(998, controller.fuel.getFluidAmount());
        assertTrue(((AbstractRecipeLogic) logic).itemOutputs.isEmpty());
        assertEquals(1, source.getFluidInputs().get(0).getAmount());
    }

    @Test
    void actualProcessingArrayDiscoveryExplicitlyRejectsInstalledProvidersWithoutCallingThem() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeModifierRuntimeTest.map();
        MTERegistry registry = GregTechAPI.mteManager.createRegistry("modifier_array_test");
        BlockMachine block = new BlockMachine();
        block.setRegistryName("modifier_array_test", "machine");
        registry.setBlock(block);
        ModifierInstalledMachine prototype = new ModifierInstalledMachine(
                new ResourceLocation("modifier_array_test", "installed"), map);
        registry.register(0, prototype.metaTileEntityId, prototype);
        MachineItemBlock item = new MachineItemBlock(block);
        item.setRegistryName("modifier_array_test", "machine");
        ConditionProcessingArray array = new ConditionProcessingArray();
        array.machineSlot.setStackInSlot(0, new ItemStack(item, 1, 0));
        array.discoverInstalledMachine();
        Recipe recipe = RecipeModifierRuntimeTest.recipe(map).inputs(new ItemStack(Items.IRON_INGOT)).build()
                .getResult();
        AbstractRecipeLogic logic = array.getRecipeMapWorkable();
        for (int i = 0; i < 20; i++) {
            assertEquals(RecipeFailureReason.MODIFIER_UNSUPPORTED, logic.evaluateRecipeEligibility(recipe).getReason());
            assertEquals(RecipeFailureReason.MODIFIER_UNSUPPORTED, logic.evaluateRecipeCheck(recipe).getReason());
        }
        assertEquals(1, array.machineSlot.getStackInSlot(0).getCount());
        assertEquals(0, logic.getProgress());
        assertNull(prototype.getWorld());
    }

    private static ModifierTurbineController turbine(RecipeMap<?> map) {
        ModifierTurbineController controller = new ModifierTurbineController(map);
        controller.fuel.fill(new FluidStack(FluidRegistry.WATER, 1000), true);
        return controller;
    }

    private static Recipe fuelRecipe(RecipeMap<SimpleRecipeBuilder> map, int eut) {
        return map.recipeBuilder().fluidInputs(new FluidStack(FluidRegistry.WATER, 1))
                .outputs(new ItemStack(Items.GOLD_NUGGET)).duration(4).EUt(eut).build().getResult();
    }

    private static long excess(LargeTurbineWorkableHandler logic) throws Exception {
        Field field = LargeTurbineWorkableHandler.class.getDeclaredField("excessVoltage");
        field.setAccessible(true);
        return field.getLong(logic);
    }
}

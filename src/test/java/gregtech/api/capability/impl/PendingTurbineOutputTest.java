package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PendingTurbineOutputTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void actualTurbineProgressAndRotorDispatchStopEnergyAndWearDuringPendingReload() {
        PendingTurbineTestController controller = new PendingTurbineTestController();
        PendingRotorTestPart rotor = new PendingRotorTestPart(controller);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.recipeEUt = 1;
        logic.fluidOutputs = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        logic.setActive(true);
        rotor.update();
        assertEquals(1, rotor.wearCalls, "Positive control reaches the actual rotor wear dispatch while active");
        assertEquals(1, rotor.getRotorSpeed());
        logic.updateWorkable();
        assertTrue(logic.isAwaitingOutputs());
        assertFalse(controller.isActive());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        NBTTagCompound saved = logic.serializeNBT();
        logic.deserializeNBT(saved);
        for (int tick = 20; tick <= 80; tick++) {
            controller.tick = tick;
            logic.updateWorkable();
            rotor.update();
        }
        assertEquals(1, rotor.wearCalls);
        assertEquals(0, rotor.getRotorSpeed());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        assertEquals(saved.getTag("FluidOutputs"), logic.serializeNBT().getTag("FluidOutputs"));
        controller.products.drain(1000, true);
        controller.tick = 81;
        logic.updateWorkable();
        assertFalse(logic.isAwaitingOutputs());
        assertFalse(controller.isActive());
        assertEquals(100, controller.products.getFluidAmount());
        assertEquals(FluidRegistry.WATER, controller.products.getFluid().getFluid());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        rotor.update();
        assertEquals(1, rotor.wearCalls);
    }

    @Test
    void ordinaryTurbineVoidingPolicyStillAllowsRejectedProductsToComplete() {
        MetaTileEntityLargeTurbine ordinary = new MetaTileEntityLargeTurbine(
                GTUtility.gregtechId("ordinary_voiding_turbine"), RecipeFailureTest.map(), GTValues.HV,
                null, null, null, false, null);
        assertTrue(ordinary.canVoidRecipeItemOutputs());
        assertTrue(ordinary.canVoidRecipeFluidOutputs());
        assertFalse(ordinary.shouldShowVoidingModeButton());
        PendingTurbineTestController controller = new PendingTurbineTestController();
        controller.voidFluids = ordinary.canVoidRecipeFluidOutputs();
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.fluidOutputs = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(0, logic.getProgress());
        assertEquals(FluidRegistry.LAVA, controller.products.getFluid().getFluid());
        assertEquals(1000, controller.products.getFluidAmount());
    }

    @Test
    void structureInvalidationRetainsTheExistingRecipeCancellationContract() {
        PendingTurbineTestController controller = new PendingTurbineTestController();
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.fluidOutputs = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        logic.tryCompleteRecipe();
        assertTrue(logic.isAwaitingOutputs());
        controller.formed = false;
        controller.invalidateStructure();
        assertFalse(logic.isAwaitingOutputs());
        assertFalse(logic.isActive());
        assertEquals(0, logic.getProgress());
        assertTrue(logic.fluidOutputs.isEmpty());
        assertFalse(logic.serializeNBT().hasKey("AwaitingOutputs"));
        assertEquals(FluidRegistry.LAVA, controller.products.getFluid().getFluid());
    }
}

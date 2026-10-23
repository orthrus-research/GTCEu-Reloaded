package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.unification.material.Materials;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidTank;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class ConditionSpecialRuntimeTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void actualCombustionHoldPrecedesLubricantCancellationAndRecoveryStillEnforcesIt() throws Exception {
        ConditionCombustionController controller = new ConditionCombustionController();
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        loadProcessingSnapshot(logic, 1, 100);
        Field boost = logic.getClass().getDeclaredField("isOxygenBoosted");
        boost.setAccessible(true);
        boost.setBoolean(logic, true);
        logic.updateWorkable();
        assertEquals(2, logic.getProgress());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        assertEquals(99, controller.lubricant.getFluidAmount());
        assertEquals(80, controller.oxygen.getFluidAmount());
        controller.environment.daytime = false;
        controller.lubricant.setFluid(null);
        for (controller.tick = 20; controller.tick <= 60; controller.tick++) logic.updateWorkable();
        assertEquals(2, logic.getProgress(),
                "Legacy lubricant check would have invalidated this work without the hold");
        assertTrue(logic.isRecipeConditionPaused());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        assertEquals(80, controller.oxygen.getFluidAmount());
        assertFalse(controller.isActive());
        controller.environment.daytime = true;
        controller.tick = 61; // Recovery refreshes legacy constraints even outside the 20-tick cadence.
        logic.updateWorkable();
        assertEquals(0, logic.getProgress(), "Recovered environment must still honor existing lubricant cancellation");
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        assertFalse(logic.isRecipeConditionPaused());
    }

    @Test
    void realTurbineRotorWearMaintenanceAndGeneratedEnergyStopWhileHeld() {
        ConditionTurbineController controller = new ConditionTurbineController();
        PendingRotorTestPart rotor = new PendingRotorTestPart(controller);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        loadProcessingSnapshot(logic, 1, 100);
        logic.updateWorkable();
        rotor.update();
        controller.update();
        assertEquals(2, logic.getProgress());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        assertTrue(rotor.wearCalls > 0);
        assertTrue(controller.maintenanceCalls > 0);
        long energy = controller.getEnergyContainer().getEnergyStored();
        int progress = logic.getProgress();
        int wear = rotor.wearCalls;
        int maintenance = controller.maintenanceCalls;
        controller.environment.daytime = false;
        for (controller.tick = 20; controller.tick <= 60; controller.tick++) {
            logic.updateWorkable();
            rotor.update();
            controller.update();
        }
        assertTrue(logic.isRecipeConditionPaused());
        assertFalse(controller.isActive());
        assertEquals(energy, controller.getEnergyContainer().getEnergyStored());
        assertEquals(progress, logic.getProgress());
        assertEquals(wear, rotor.wearCalls);
        assertEquals(maintenance, controller.maintenanceCalls);
        assertEquals(0, rotor.getRotorSpeed());
        controller.environment.daytime = true;
        logic.updateWorkable();
        rotor.update();
        assertEquals(energy + 1, controller.getEnergyContainer().getEnergyStored());
        assertEquals(wear + 1, rotor.wearCalls);
    }

    @Test
    void bothComputationModesSkipEvenAbsentResourceProbesDuringHoldAndResumeLegacyBehavior() {
        for (ComputationRecipeLogic.ComputationType type : ComputationRecipeLogic.ComputationType.values()) {
            ConditionComputationController controller = new ConditionComputationController(
                    ConditionRecipeSelectionTest.map());
            int[] energyDraws = { 0 };
            ComputationRecipeLogic logic = new ComputationRecipeLogic(controller, type) {

                @Override
                protected boolean drawEnergy(long eut, boolean simulate) {
                    if (!simulate) energyDraws[0]++;
                    return true;
                }

                @Override
                protected boolean shouldSearchForRecipes() {
                    return false;
                }
            };
            controller.install(logic);
            NBTTagCompound running = processingSnapshot(logic, 4, 100);
            running.setInteger("RecipeCWUt", 8);
            running.setBoolean("IsDurationTotalCWU", type == ComputationRecipeLogic.ComputationType.SPORADIC);
            logic.deserializeNBT(running);
            controller.runFormed();
            assertEquals(1, energyDraws[0]);
            assertEquals(1, controller.actualCWU);
            int progress = logic.getProgress();
            int probes = controller.simulatedCWU;
            controller.computationAvailable = false;
            controller.environment.daytime = false;
            for (controller.tick = 20; controller.tick <= 40; controller.tick++) {
                controller.runFormed();
                controller.update();
            }
            assertTrue(logic.isRecipeConditionPaused());
            assertEquals(progress, logic.getProgress());
            assertEquals(0, logic.getCurrentDrawnCWUt());
            assertEquals(1, energyDraws[0]);
            assertEquals(1, controller.actualCWU);
            assertEquals(probes, controller.simulatedCWU);
            assertEquals(0, controller.maintenanceCalls);
            controller.environment.daytime = true;
            controller.runFormed();
            assertEquals(RecipeFailureReason.COMPUTATION, logic.getRecipeFailure().getReason());
            assertEquals(2, energyDraws[0], "Existing computation logic draws EU before discovering missing CWU");
            assertEquals(probes + 1, controller.simulatedCWU);
            assertEquals(1, controller.actualCWU);
            assertEquals(type == ComputationRecipeLogic.ComputationType.SPORADIC ? progress : Math.max(1, progress - 2),
                    logic.getProgress());
        }
    }

    @Test
    void actualSteamLogicDoesNotDrainSteamWhileConditionHeld() {
        ConditionRuntimeMachine machine = ConditionRecipeSelectionTest.machine();
        FluidTank steam = new FluidTank(Materials.Steam.getFluid(500), 1000);
        RecipeLogicSteam logic = new RecipeLogicSteam(machine, ConditionRecipeSelectionTest.map(), false, steam, 1);
        loadProcessingSnapshot(logic, 4, 100);
        machine.tick = 1;
        logic.update();
        int steamAfterProgress = steam.getFluidAmount();
        int progress = logic.getProgress();
        assertTrue(steamAfterProgress < 500);
        machine.environment.daytime = false;
        for (machine.tick = 2; machine.tick <= 40; machine.tick++) logic.update();
        assertEquals(steamAfterProgress, steam.getFluidAmount());
        assertEquals(progress, logic.getProgress());
        assertFalse(logic.isNeedsVenting());
        assertFalse(logic.isActive());
        machine.environment.daytime = true;
        logic.update();
        assertTrue(steam.getFluidAmount() < steamAfterProgress);
        assertEquals(progress + 1, logic.getProgress());
    }

    private static void loadProcessingSnapshot(AbstractRecipeLogic logic, int eut, int duration) {
        logic.deserializeNBT(processingSnapshot(logic, eut, duration));
    }

    private static NBTTagCompound processingSnapshot(AbstractRecipeLogic logic, int eut, int duration) {
        logic.progressTime = 1;
        logic.maxProgressTime = duration;
        logic.recipeEUt = eut;
        NBTTagCompound running = logic.serializeNBT();
        running.setTag("ActiveRecipeConditions",
                RecipeConditionSet.EMPTY.add(RecipeConditions.daytime(false)).serialize());
        return running;
    }
}

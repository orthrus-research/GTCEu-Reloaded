package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFusionReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static gregtech.api.capability.impl.RecipeFailureTest.assertReason;
import static org.junit.jupiter.api.Assertions.*;

class RecipeFailureConstraintTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void steamVentingAndMissingSteamAreDifferentObservedFailures() {
        RecipeFailureTestMachine machine = RecipeFailureTest.populated();
        FluidTank tank = new FluidTank(1000);
        RecipeLogicSteam logic = new RecipeLogicSteam(machine, RecipeFailureTest.map(), false, tank, 1);
        Recipe recipe = RecipeMaps.FURNACE_RECIPES.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).build().getResult();
        logic.setNeedsVenting(true);
        assertEquals(RecipeFailureReason.VENTING, logic.evaluateRecipeEligibility(recipe).getReason());
        assertTrue(logic.isNeedsVenting());
        assertEquals(0, tank.getFluidAmount());
        logic.setNeedsVenting(false);
        logic.progressTime = 1;
        logic.maxProgressTime = 100;
        logic.recipeEUt = 1;
        logic.updateRecipeProgress();
        assertReason(logic, RecipeFailureReason.NOT_ENOUGH_STEAM);
        assertEquals(0, tank.getFluidAmount());
    }

    @Test
    void realBlastFurnaceCaptureReportsTheCheckedTemperature() throws ReflectiveOperationException {
        MetaTileEntityElectricBlastFurnace furnace = new MetaTileEntityElectricBlastFurnace(
                GTUtility.gregtechId("failure_blast_test"));
        Field temperature = MetaTileEntityElectricBlastFurnace.class.getDeclaredField("blastFurnaceTemperature");
        temperature.setAccessible(true);
        temperature.setInt(furnace, 1500);
        Recipe recipe = RecipeMaps.BLAST_RECIPES.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).blastFurnaceTemp(1501).build().getResult();
        RecipeFailure failure = furnace.getRecipeMapWorkable().evaluateRecipeEligibility(recipe);
        assertEquals(RecipeFailureReason.TEMPERATURE, failure.getReason());
        assertEquals(1501, failure.getRequired());
        assertEquals(1500, failure.getAvailable());
        temperature.setInt(furnace, 1501);
        assertTrue(furnace.getRecipeMapWorkable().evaluateRecipeEligibility(recipe).isEmpty());
    }

    @Test
    void realFusionCaptureReportsCapacityAndHeatGapWithoutSpendingEnergy() throws ReflectiveOperationException {
        MetaTileEntityFusionReactor fusion = new MetaTileEntityFusionReactor(GTUtility.gregtechId("failure_fusion"),
                6) {

            @Override
            public World getWorld() {
                return DummyWorld.INSTANCE;
            }

            @Override
            public void markDirty() {}
        };
        EnergyContainerHandler energy = EnergyContainerHandler.receiverContainer(fusion, 1000, 32, 1);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setLong("EnergyStored", 600);
        energy.deserializeNBT(saved);
        Field energyField = RecipeMapMultiblockController.class.getDeclaredField("energyContainer");
        energyField.setAccessible(true);
        energyField.set(fusion, energy);
        Field heat = MetaTileEntityFusionReactor.class.getDeclaredField("heat");
        heat.setAccessible(true);
        heat.setLong(fusion, 100);
        AbstractRecipeLogic logic = fusion.getRecipeMapWorkable();
        RecipeFailure capacity = logic.evaluateRecipeEligibility(fusionRecipe(1001));
        assertEquals(RecipeFailureReason.FUSION_CAPACITY, capacity.getReason());
        assertEquals(1001, capacity.getRequired());
        assertEquals(1000, capacity.getAvailable());
        RecipeFailure startup = logic.evaluateRecipeEligibility(fusionRecipe(800));
        assertEquals(RecipeFailureReason.FUSION_START, startup.getReason());
        assertEquals(700, startup.getRequired());
        assertEquals(600, startup.getAvailable());
        assertEquals(600, energy.getEnergyStored());
        assertEquals(100, heat.getLong(fusion));
        assertTrue(logic.evaluateRecipeCheck(fusionRecipe(500)).isEmpty());
        assertEquals(200, energy.getEnergyStored());
        assertEquals(500, heat.getLong(fusion));
    }

    @Test
    void computationDiagnosticsUseOnlyTheRequestsAlreadyMadeByTheRecipeLogic() throws ReflectiveOperationException {
        RecipeFailureComputationController controller = new RecipeFailureComputationController(
                RecipeMaps.RESEARCH_STATION_RECIPES);
        int[] draws = { 0 };
        ComputationRecipeLogic logic = new ComputationRecipeLogic(controller,
                ComputationRecipeLogic.ComputationType.STEADY) {

            @Override
            protected boolean drawEnergy(long eut, boolean simulate) {
                if (!simulate) draws[0]++;
                return true;
            }
        };
        controller.install(logic);
        Recipe recipe = RecipeMaps.RESEARCH_STATION_RECIPES.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).CWUt(4).build().getResult();
        assertEquals(RecipeFailureReason.COMPUTATION, logic.evaluateRecipeCheck(recipe).getReason());
        assertEquals(1, controller.simulatedRequests);
        assertEquals(0, controller.actualRequests);
        for (int i = 0; i < 100; i++) logic.getRecipeFailure();
        assertEquals(1, controller.simulatedRequests);
        Field requirement = ComputationRecipeLogic.class.getDeclaredField("recipeCWUt");
        requirement.setAccessible(true);
        requirement.setInt(logic, 4);
        logic.progressTime = 5;
        logic.maxProgressTime = 100;
        logic.recipeEUt = 1;
        logic.updateRecipeProgress();
        assertReason(logic, RecipeFailureReason.COMPUTATION);
        assertEquals(2, controller.simulatedRequests);
        assertEquals(0, controller.actualRequests);
        assertEquals(1, draws[0]);
        controller.available = 4;
        logic.updateRecipeProgress();
        assertReason(logic, RecipeFailureReason.NONE);
        assertEquals(3, controller.simulatedRequests);
        assertEquals(1, controller.actualRequests);
        assertEquals(2, draws[0]);
    }

    private static Recipe fusionRecipe(long startup) {
        return RecipeMaps.FUSION_RECIPES.recipeBuilder().fluidInputs(new FluidStack(FluidRegistry.WATER, 1))
                .fluidOutputs(new FluidStack(FluidRegistry.LAVA, 1)).EUt(1).duration(100).EUToStart(startup)
                .build().getResult();
    }

    @Test
    void processingArrayForwardsItsInstalledMachinesFailureWithoutRepeatingTheCheck()
                                                                                      throws ReflectiveOperationException {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        Recipe recipe = RecipeFailureTest.add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .cleanroom(CleanroomType.CLEANROOM));
        RecipeFailureTestMachine installed = RecipeFailureTest.populated();
        RecipeFailureTestLogic installedLogic = new RecipeFailureTestLogic(installed, map);
        MetaTileEntityProcessingArray array = new MetaTileEntityProcessingArray(GTUtility.gregtechId("failure_array"),
                0);
        AbstractRecipeLogic logic = array.getRecipeMapWorkable();
        Field machine = logic.getClass().getDeclaredField("mte");
        machine.setAccessible(true);
        machine.set(logic, installed);
        assertEquals(RecipeFailureReason.CLEANROOM_MISSING, logic.evaluateRecipeEligibility(recipe).getReason());
        assertEquals(0, installedLogic.finalChecks);
        assertEquals(RecipeFailureReason.CLEANROOM_MISSING, logic.evaluateRecipeCheck(recipe).getReason());
        assertEquals(1, installedLogic.finalChecks);
        assertEquals(0, installedLogic.preparations);
        assertEquals(8, installed.getImportItems().getStackInSlot(0).getCount());
    }
}

package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
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
import net.minecraftforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RecipeCandidateConstraintTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void steamVentingIsAnEligibilityConstraintWithoutChangingSteamOrVentingState() {
        RecipeCandidateTestMachine machine = new RecipeCandidateTestMachine();
        FluidTank tank = new FluidTank(1000);
        RecipeLogicSteam logic = new RecipeLogicSteam(machine, RecipeCandidateSelectionTest.map(), false, tank, 1);
        Recipe recipe = RecipeCandidateSelectionTest.add(RecipeCandidateSelectionTest.map(), Items.IRON_INGOT);
        logic.setNeedsVenting(true);
        for (int i = 0; i < 3; i++) assertFalse(logic.isRecipeEligible(recipe));
        assertTrue(logic.isNeedsVenting());
        assertEquals(0, tank.getFluidAmount());
        logic.setNeedsVenting(false);
        assertTrue(logic.isRecipeEligible(recipe));
    }

    @Test
    void blastTemperatureUsesTheSameBoundaryWithoutCallingLegacyChecks() throws ReflectiveOperationException {
        MetaTileEntityElectricBlastFurnace furnace = new MetaTileEntityElectricBlastFurnace(
                GTUtility.gregtechId("candidate_blast_test"));
        Field temperature = MetaTileEntityElectricBlastFurnace.class.getDeclaredField("blastFurnaceTemperature");
        temperature.setAccessible(true);
        temperature.setInt(furnace, 1500);
        Recipe allowed = RecipeMaps.BLAST_RECIPES.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).blastFurnaceTemp(1500).build().getResult();
        Recipe blocked = RecipeMaps.BLAST_RECIPES.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).blastFurnaceTemp(1501).build().getResult();
        assertTrue(furnace.isRecipeEligible(allowed));
        assertFalse(furnace.isRecipeEligible(blocked));
        assertEquals(1500, temperature.getInt(furnace));
    }

    @Test
    void fusionCandidateProbesDoNotSpendStartupEnergyOrIncreaseHeat() throws ReflectiveOperationException {
        MetaTileEntityFusionReactor fusion = new MetaTileEntityFusionReactor(GTUtility.gregtechId("candidate_fusion"),
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
        Recipe selected = fusionRecipe(500);
        Recipe unavailable = fusionRecipe(800);
        Recipe tooLarge = fusionRecipe(1001);
        AbstractRecipeLogic logic = fusion.getRecipeMapWorkable();
        for (int i = 0; i < 3; i++) {
            assertTrue(logic.isRecipeEligible(selected));
            assertFalse(logic.isRecipeEligible(unavailable));
            assertFalse(logic.isRecipeEligible(tooLarge));
        }
        assertEquals(600, energy.getEnergyStored());
        assertEquals(100, heat.getLong(fusion));
        assertTrue(logic.checkRecipe(selected));
        assertEquals(200, energy.getEnergyStored());
        assertEquals(500, heat.getLong(fusion));
    }

    private static Recipe fusionRecipe(long startup) {
        return RecipeMaps.FUSION_RECIPES.recipeBuilder().fluidInputs(new FluidStack(FluidRegistry.WATER, 1))
                .fluidOutputs(new FluidStack(FluidRegistry.LAVA, 1)).EUt(1).duration(100).EUToStart(startup)
                .build().getResult();
    }

    @Test
    void processingArrayKeepsInstalledMachineVoltageAndUsesOnlyItsPureHook() throws ReflectiveOperationException {
        RecipeMap<SimpleRecipeBuilder> map = RecipeCandidateSelectionTest.map();
        Recipe blocked = RecipeCandidateSelectionTest.add(map, Items.IRON_INGOT);
        Recipe highVoltage = map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.APPLE)).EUt(128).duration(100).build().getResult();
        assertTrue(map.compileRecipe(highVoltage));
        RecipeCandidateTestMachine installed = RecipeCandidateSelectionTest.populatedMachine();
        RecipeCandidateTestLogic installedLogic = new RecipeCandidateTestLogic(installed, map);
        installedLogic.eligibility = recipe -> recipe != blocked;
        MetaTileEntityProcessingArray array = new MetaTileEntityProcessingArray(GTUtility.gregtechId("candidate_array"),
                0) {

            {
                // Isolate the installed-machine lookup from block-item registration and hatch discovery.
                this.recipeMapWorkable = new ProcessingArrayWorkable(this) {

                    @Override
                    public boolean isRecipeMapValid(RecipeMap<?> recipeMap) {
                        return true;
                    }
                };
            }
        };
        AbstractRecipeLogic logic = array.getRecipeMapWorkable();
        Class<?> workableClass = logic.getClass().getSuperclass();
        set(workableClass, logic, "mte", installed);
        set(workableClass, logic, "machineVoltage", 32L);
        set(workableClass, logic, "activeRecipeMap", map);
        set(RecipeMapMultiblockController.class, array, "energyContainer",
                EnergyContainerHandler.receiverContainer(array, 10000, 128, 1));
        Method lookup = AbstractRecipeLogic.class.getDeclaredMethod("findRecipe", long.class,
                IItemHandlerModifiable.class, IMultipleTankHandler.class);
        lookup.setAccessible(true);
        Recipe result = (Recipe) lookup.invoke(logic, 128L, installed.getImportItems(), new FluidTankList(false));
        assertSame(blocked, result, "The 128 EU/t alternative must remain outside the installed 32 EU/t machine limit");
        assertFalse(logic.isRecipeEligible(blocked));
        assertEquals(0, installedLogic.finalChecks);
        assertEquals(0, installedLogic.preparations);
        assertEquals(8, installed.getImportItems().getStackInSlot(0).getCount());
        assertEquals(8, installed.getImportItems().getStackInSlot(1).getCount());
    }

    private static void set(Class<?> owner, Object instance, String name,
                            Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}

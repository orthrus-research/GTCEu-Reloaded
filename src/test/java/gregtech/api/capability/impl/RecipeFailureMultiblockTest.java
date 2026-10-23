package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.common.ConfigHolder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static gregtech.api.capability.impl.RecipeFailureTest.assertReason;
import static org.junit.jupiter.api.Assertions.*;

class RecipeFailureMultiblockTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void mufflerGatePublishesWithoutTickingAndClearsBeforeTheNextActualCheck() {
        RecipeFailureTestController controller = new RecipeFailureTestController(RecipeFailureTest.map());
        int[] ticks = { 0 };
        MultiblockRecipeLogic logic = new MultiblockRecipeLogic(controller) {

            @Override
            public void updateWorkable() {
                ticks[0]++;
                super.updateWorkable();
            }
        };
        controller.install(logic);
        controller.mufflerFree = false;
        controller.runFormed();
        assertReason(logic, RecipeFailureReason.MUFFLER_OBSTRUCTED);
        assertEquals(0, ticks[0]);
        assertEquals(1, controller.mufflerChecks);
        for (int i = 0; i < 10; i++) assertReason(logic, RecipeFailureReason.MUFFLER_OBSTRUCTED);
        assertEquals(1, controller.mufflerChecks, "Display getters must not repeat the world gate");
        logic.setWorkingEnabled(false);
        assertReason(logic, RecipeFailureReason.DISABLED);
        controller.formed = false;
        assertReason(logic, RecipeFailureReason.UNFORMED);
        controller.formed = true;
        logic.setWorkingEnabled(true);
        controller.mufflerFree = true;
        controller.runFormed();
        assertEquals(1, ticks[0]);
        assertEquals(2, controller.mufflerChecks);
        assertReason(logic, RecipeFailureReason.NO_RECIPE);
    }

    @Test
    void maintenanceReasonExistsOnlyAtTheActualIdleStartGate() {
        boolean previousMaintenance = ConfigHolder.machines.enableMaintenance;
        ConfigHolder.machines.enableMaintenance = true;
        try {
            RecipeFailureTestController controller = new RecipeFailureTestController(RecipeFailureTest.map());
            MultiblockRecipeLogic logic = new MultiblockRecipeLogic(controller) {

                @Override
                protected boolean drawEnergy(long eut, boolean simulate) {
                    return true;
                }
            };
            controller.install(logic);
            controller.maintenanceProblems = 6;
            logic.trySearchNewRecipe();
            assertReason(logic, RecipeFailureReason.MAINTENANCE);
            logic.progressTime = 1;
            logic.maxProgressTime = 100;
            logic.recipeEUt = 1;
            logic.updateWorkable();
            assertReason(logic, RecipeFailureReason.NONE);
            assertEquals(2, logic.progressTime, "Maintenance start restrictions must not become running restrictions");
            logic.progressTime = 0;
            controller.maintenanceProblems = 5;
            logic.trySearchNewRecipe();
            assertReason(logic, RecipeFailureReason.NO_RECIPE);
        } finally {
            ConfigHolder.machines.enableMaintenance = previousMaintenance;
        }
    }

    @Test
    void distinctBlockedBusesRetainTheirOwnReasonAndAValidAlternativeClearsIt() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        Recipe first = RecipeFailureTest.add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .dimension(123456));
        Recipe second = RecipeFailureTest.add(map, map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .cleanroom(CleanroomType.CLEANROOM));
        RecipeFailureTestController controller = new RecipeFailureTestController(map);
        controller.distinct = true;
        ItemStackHandler iron = new ItemStackHandler(1);
        ItemStackHandler gold = new ItemStackHandler(1);
        iron.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        gold.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 8));
        List<IItemHandlerModifiable> buses = Arrays.asList(iron, gold);
        int[] prepared = { 0 };
        MultiblockRecipeLogic logic = new MultiblockRecipeLogic(controller) {

            @Override
            protected List<IItemHandlerModifiable> getInputBuses() {
                return buses;
            }

            @Override
            protected IItemHandlerModifiable getInputInventory() {
                return new ItemHandlerList(buses);
            }

            @Override
            protected IMultipleTankHandler getInputTank() {
                return new FluidTankList(false);
            }

            @Override
            public long getMaxVoltage() {
                return 32;
            }

            @Override
            protected boolean prepareRecipeDistinct(Recipe recipe) {
                prepared[0]++;
                return recipe.matches(true, currentDistinctInputBus, getInputTank());
            }
        };
        controller.install(logic);
        boolean previousCleanMultiblocks = ConfigHolder.machines.cleanMultiblocks;
        ConfigHolder.machines.cleanMultiblocks = false;
        try {
            logic.trySearchNewRecipeDistinct();
            assertSame(first, logic.getPreviousRecipe());
            assertReason(logic, RecipeFailureReason.DIMENSION);
            assertEquals(0, prepared[0]);
            RecipeFailureCleanroom cleanroom = new RecipeFailureCleanroom();
            cleanroom.clean = true;
            cleanroom.correctType = true;
            controller.setCleanroom(cleanroom);
            controller.tick += 20;
            logic.trySearchNewRecipeDistinct();
            assertSame(second, logic.getPreviousRecipe());
            assertReason(logic, RecipeFailureReason.NONE);
            assertEquals(1, prepared[0]);
            assertEquals(8, iron.getStackInSlot(0).getCount());
            assertEquals(7, gold.getStackInSlot(0).getCount());
        } finally {
            ConfigHolder.machines.cleanMultiblocks = previousCleanMultiblocks;
        }
    }
}

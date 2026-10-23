package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DistinctRecipeCandidateTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void blockedCachedBusFallsBackAndOnlySelectedBusIsConsumed() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeCandidateSelectionTest.map();
        Recipe iron = RecipeCandidateSelectionTest.add(map, Items.IRON_INGOT);
        Recipe gold = RecipeCandidateSelectionTest.add(map, Items.GOLD_INGOT);
        RecipeCandidateTestController controller = new RecipeCandidateTestController(map);
        controller.eligibility = recipe -> recipe == gold;
        ItemStackHandler first = bus(new ItemStack(Items.IRON_INGOT, 8));
        ItemStackHandler second = bus(new ItemStack(Items.GOLD_INGOT, 8));
        RecipeCandidateDistinctLogic logic = new RecipeCandidateDistinctLogic(controller, Arrays.asList(first, second));
        logic.previousRecipe = iron;
        logic.trySearchNewRecipeDistinct();
        assertSame(gold, logic.previousRecipe);
        assertEquals(1, logic.lastRecipeIndex);
        assertEquals(8, first.getStackInSlot(0).getCount());
        assertEquals(7, second.getStackInSlot(0).getCount());
        assertEquals(1, controller.checks);
        assertEquals(1, controller.commits);
        assertEquals(1, logic.preparations);
        assertTrue(logic.invalidatedInputList.isEmpty());
    }

    @Test
    void allEnvironmentBlockedBusesRecoverWithoutBecomingInvalidatedInputs() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeCandidateSelectionTest.map();
        Recipe recipe = RecipeCandidateSelectionTest.add(map, Items.IRON_INGOT);
        RecipeCandidateTestController controller = new RecipeCandidateTestController(map);
        controller.eligibility = candidate -> false;
        ItemStackHandler empty = new ItemStackHandler(1);
        ItemStackHandler stocked = bus(new ItemStack(Items.IRON_INGOT, 8));
        RecipeCandidateDistinctLogic logic = new RecipeCandidateDistinctLogic(controller,
                Arrays.asList(empty, stocked));
        logic.runWhenReady();
        assertEquals(Arrays.asList(empty), logic.invalidatedInputList);
        assertSame(recipe, logic.previousRecipe);
        assertEquals(0, controller.checks);
        assertEquals(0, controller.commits);
        controller.eligibility = candidate -> true;
        for (controller.tick = 1; controller.tick < 20; controller.tick++) logic.runWhenReady();
        assertEquals(0, logic.preparations);
        logic.runWhenReady();
        assertEquals(1, controller.checks);
        assertEquals(1, controller.commits);
        assertEquals(1, logic.preparations);
        assertEquals(7, stocked.getStackInSlot(0).getCount());
    }

    @Test
    void finalRejectionDoesNotTryAnotherBusOrRepeatCachedChecks() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeCandidateSelectionTest.map();
        Recipe iron = RecipeCandidateSelectionTest.add(map, Items.IRON_INGOT);
        RecipeCandidateSelectionTest.add(map, Items.GOLD_INGOT);
        RecipeCandidateTestController controller = new RecipeCandidateTestController(map);
        controller.finalAllowed = false;
        ItemStackHandler first = bus(new ItemStack(Items.IRON_INGOT, 8));
        ItemStackHandler second = bus(new ItemStack(Items.GOLD_INGOT, 8));
        RecipeCandidateDistinctLogic logic = new RecipeCandidateDistinctLogic(controller, Arrays.asList(first, second));
        logic.previousRecipe = iron;
        logic.trySearchNewRecipeDistinct();
        assertEquals(1, controller.checks);
        assertEquals(0, controller.commits);
        assertEquals(0, logic.preparations);
        assertEquals(8, first.getStackInSlot(0).getCount());
        assertEquals(8, second.getStackInSlot(0).getCount());
    }

    private static ItemStackHandler bus(ItemStack input) {
        ItemStackHandler bus = new ItemStackHandler(1);
        bus.setStackInSlot(0, input);
        return bus;
    }
}

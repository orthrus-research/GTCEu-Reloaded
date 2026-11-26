package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSearchSchedulerTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void aNewSchedulerHasObservedItsMapAndWantsAWakeup() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        RecipeSearchScheduler scheduler = new RecipeSearchScheduler(map);
        assertFalse(scheduler.hasMapChanged(map));
        assertTrue(scheduler.isWakeupRequested(), "The first update must run");
        assertTrue(scheduler.isSearchDue(0));
    }

    @Test
    void anotherMapOrANewRecipeIsAChangeUntilObserved() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        RecipeSearchScheduler scheduler = new RecipeSearchScheduler(map);
        RecipeMap<SimpleRecipeBuilder> other = RecipeFailureTest.map();
        assertTrue(scheduler.hasMapChanged(other));
        assertTrue(scheduler.hasMapChanged(null));

        RecipeFailureTest.add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
        assertTrue(scheduler.hasMapChanged(map), "A recipe added to the observed map changes its revision");
        scheduler.observe(map);
        assertFalse(scheduler.hasMapChanged(map));

        scheduler.observe(null);
        assertFalse(scheduler.hasMapChanged(null));
        assertTrue(scheduler.hasMapChanged(map));
    }

    @Test
    void aWakeupStaysRequestedUntilCleared() {
        RecipeSearchScheduler scheduler = new RecipeSearchScheduler(null);
        scheduler.clearWakeup();
        assertFalse(scheduler.isWakeupRequested());
        scheduler.requestWakeup();
        scheduler.requestWakeup();
        assertTrue(scheduler.isWakeupRequested());
        scheduler.clearWakeup();
        assertFalse(scheduler.isWakeupRequested());
    }

    @Test
    void aDeferredSearchWaitsTwentyTicksUnlessTheDeferralIsCleared() {
        RecipeSearchScheduler scheduler = new RecipeSearchScheduler(null);
        scheduler.defer(100);
        assertFalse(scheduler.isSearchDue(100));
        assertFalse(scheduler.isSearchDue(119));
        assertTrue(scheduler.isSearchDue(120));
        scheduler.clearDeferral();
        assertTrue(scheduler.isSearchDue(100));
    }

    @Test
    void theSafetyCadenceIsEveryTwentiethTick() {
        assertTrue(RecipeSearchScheduler.isSafetyTick(0));
        assertTrue(RecipeSearchScheduler.isSafetyTick(40));
        assertFalse(RecipeSearchScheduler.isSafetyTick(19));
        assertFalse(RecipeSearchScheduler.isSafetyTick(21));
    }
}

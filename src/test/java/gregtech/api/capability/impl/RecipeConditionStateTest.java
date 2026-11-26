package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeConditionStateTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeConditionSet night() {
        return RecipeConditionSet.EMPTY.add(RecipeConditions.daytime(false));
    }

    @Test
    void aStartedRecipeRunsUnpausedUntilItsConditionsFail() {
        RecipeConditionState state = new RecipeConditionState();
        assertTrue(state.getActive().isEmpty());
        assertFalse(state.isPaused());

        RecipeConditionSet conditions = night();
        state.start(conditions);
        assertSame(conditions, state.getActive());
        assertFalse(state.isPaused());
        assertFalse(state.resume(), "An unpaused recipe has nothing to resume");

        state.pause();
        state.pause();
        assertTrue(state.isPaused());
        assertTrue(state.resume());
        assertFalse(state.isPaused());
        assertFalse(state.resume());
    }

    @Test
    void startingOrClearingForgetsAPause() {
        RecipeConditionState state = new RecipeConditionState();
        state.start(night());
        state.pause();
        state.start(night());
        assertFalse(state.isPaused());
        state.pause();
        state.clear();
        assertFalse(state.isPaused());
        assertTrue(state.getActive().isEmpty());
    }

    @Test
    void onlyConditionsThatExistAreSavedUnderTheirKey() {
        RecipeConditionState state = new RecipeConditionState();
        NBTTagCompound empty = new NBTTagCompound();
        state.writeToNBT(empty);
        assertTrue(empty.isEmpty());

        RecipeConditionSet conditions = night();
        state.start(conditions);
        NBTTagCompound tag = new NBTTagCompound();
        state.writeToNBT(tag);
        assertEquals(1, tag.getKeySet().size());
        assertEquals(conditions.serialize(), tag.getCompoundTag("ActiveRecipeConditions"));
    }

    @Test
    void aLoadedRunningRecipeWithConditionsStartsPausedUnlessItWaitsForOutputSpace() {
        NBTTagCompound tag = new NBTTagCompound();
        RecipeConditionState saved = new RecipeConditionState();
        saved.start(night());
        saved.writeToNBT(tag);

        RecipeConditionState running = new RecipeConditionState();
        running.readFromNBT(tag, true, false);
        assertTrue(running.isPaused(), "Conditions are checked again before a loaded recipe progresses");
        assertEquals(night().serialize(), running.getActive().serialize());

        RecipeConditionState awaiting = new RecipeConditionState();
        awaiting.readFromNBT(tag, true, true);
        assertFalse(awaiting.isPaused());
        assertFalse(awaiting.getActive().isEmpty());
    }

    @Test
    void conditionsAreReadOnlyForARunningRecipe() {
        NBTTagCompound tag = new NBTTagCompound();
        RecipeConditionState saved = new RecipeConditionState();
        saved.start(night());
        saved.writeToNBT(tag);

        RecipeConditionState idle = new RecipeConditionState();
        idle.start(night());
        idle.pause();
        idle.readFromNBT(tag, false, false);
        assertTrue(idle.getActive().isEmpty());
        assertFalse(idle.isPaused());

        RecipeConditionState unconditioned = new RecipeConditionState();
        unconditioned.readFromNBT(new NBTTagCompound(), true, false);
        assertTrue(unconditioned.getActive().isEmpty());
        assertFalse(unconditioned.isPaused());
    }
}

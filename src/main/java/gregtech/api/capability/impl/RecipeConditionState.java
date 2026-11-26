package gregtech.api.capability.impl;

import gregtech.api.recipes.conditions.RecipeConditionSet;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The conditions of the recipe a logic is running, and whether they hold its progress. They are saved with the
 * running recipe under {@value #NBT_KEY}, and only when there are any.
 * <p>
 * It holds state only. {@link AbstractRecipeLogic} evaluates the conditions, through its
 * {@code createRecipeConditionContext}, and performs the side effects of a pause or resume.
 */
final class RecipeConditionState {

    static final String NBT_KEY = "ActiveRecipeConditions";

    private RecipeConditionSet active = RecipeConditionSet.EMPTY;
    private boolean paused;

    RecipeConditionSet getActive() {
        return active;
    }

    /** @return the last observed hold, not a fresh world check */
    boolean isPaused() {
        return paused;
    }

    /** Takes the conditions of a recipe that starts now; it starts unpaused. */
    void start(RecipeConditionSet conditions) {
        this.active = conditions;
        this.paused = false;
    }

    /** Forgets the conditions of a recipe that completed or was abandoned. */
    void clear() {
        this.active = RecipeConditionSet.EMPTY;
        this.paused = false;
    }

    void pause() {
        this.paused = true;
    }

    /** @return whether progress was paused and now resumes */
    boolean resume() {
        if (!paused) return false;
        this.paused = false;
        return true;
    }

    /** Writes the conditions of the running recipe, if it has any. */
    void writeToNBT(NBTTagCompound tag) {
        if (!active.isEmpty()) tag.setTag(NBT_KEY, active.serialize());
    }

    /**
     * Reads the conditions of a running recipe. A loaded recipe with conditions starts paused, so they are checked
     * before it progresses, unless its outputs only wait for space.
     *
     * @param running         whether a recipe was running when the logic was saved
     * @param awaitingOutputs whether that recipe had finished and only waited for output space
     */
    void readFromNBT(NBTTagCompound tag, boolean running, boolean awaitingOutputs) {
        this.active = running && tag.hasKey(NBT_KEY) ? RecipeConditionSet.deserialize(tag.getTag(NBT_KEY)) :
                RecipeConditionSet.EMPTY;
        this.paused = !awaitingOutputs && !active.isEmpty();
    }
}

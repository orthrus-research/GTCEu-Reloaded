package gregtech.api.capability.impl;

import gregtech.api.recipes.RecipeMap;

import org.jetbrains.annotations.Nullable;

/**
 * When an idle recipe logic searches again: a pending wake request, the tick before which a deferred search waits,
 * and the recipe map and revision its last search saw.
 * <p>
 * It holds state only. {@link AbstractRecipeLogic} decides when to defer, wake and observe, and keeps
 * {@code deferRecipeSearch}, {@code requestRecipeLogicWakeup} and {@code shouldSearchForRecipes} as the methods a
 * subclass calls or overrides.
 */
final class RecipeSearchScheduler {

    /** Ticks a deferred search waits, and the cadence at which an idle logic is still updated. */
    static final int RETRY_TICKS = 20;

    private long nextSearchTick;
    private boolean wakeupRequested = true;
    private RecipeMap<?> observedMap;
    private long observedRevision;

    RecipeSearchScheduler(@Nullable RecipeMap<?> map) {
        observe(map);
    }

    void requestWakeup() {
        this.wakeupRequested = true;
    }

    boolean isWakeupRequested() {
        return wakeupRequested;
    }

    void clearWakeup() {
        this.wakeupRequested = false;
    }

    /** @return whether the map is another one than the last observed, or has changed its recipes since */
    boolean hasMapChanged(@Nullable RecipeMap<?> map) {
        return map != observedMap || (map != null && map.getRecipeRevision() != observedRevision);
    }

    void observe(@Nullable RecipeMap<?> map) {
        this.observedMap = map;
        this.observedRevision = map == null ? 0 : map.getRecipeRevision();
    }

    /** Lets the next search wait {@value #RETRY_TICKS} ticks after the given one. */
    void defer(long tick) {
        this.nextSearchTick = tick + RETRY_TICKS;
    }

    void clearDeferral() {
        this.nextSearchTick = 0;
    }

    boolean isSearchDue(long tick) {
        return tick >= nextSearchTick;
    }

    /** @return whether an idle logic is updated on this tick even with nothing else to do */
    static boolean isSafetyTick(long tick) {
        return tick % RETRY_TICKS == 0;
    }
}

package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

/**
 * How a steam recipe logic counts steam as energy, shared by {@link RecipeLogicSteam} and
 * {@link SteamMultiblockRecipeLogic}: stored steam counts as a fixed number of EU per millibucket, a recipe's EU/t is
 * drawn as steam, and a recipe starts only with the steam for its whole run. The steam stays in each logic's tanks;
 * the logic passes in what they hold and how to drain them.
 */
final class SteamAccounting {

    /** Steam logics find and run recipes up to LV. */
    static final long MAX_VOLTAGE = GTValues.V[GTValues.LV];

    private final double conversionRate;

    /** @param conversionRate the EU one millibucket of steam counts as */
    SteamAccounting(double conversionRate) {
        this.conversionRate = conversionRate;
    }

    /** @return the energy the stored steam counts as, rounded up */
    long getEnergyStored(long steamStored) {
        return (long) Math.ceil(steamStored * conversionRate);
    }

    /** @return the energy the steam capacity counts as, rounded down */
    long getEnergyCapacity(long steamCapacity) {
        return (long) Math.floor(steamCapacity * conversionRate);
    }

    /**
     * Draws energy as steam, rounded up to whole millibuckets.
     *
     * @param steamStored the steam the tanks hold now
     * @param tanks       the tanks to drain
     * @return whether the tanks held and gave, or for a simulation would give, the steam
     */
    boolean drawEnergy(long eut, boolean simulate, long steamStored, Drain tanks) {
        int resultDraw = GTUtility.safeCastLongToInt((long) Math.ceil(eut / conversionRate));
        return resultDraw >= 0 && steamStored >= resultDraw && tanks.drain(resultDraw, !simulate) != null;
    }

    /** @return whether the recipe consumes steam; one that does not is checked as any other recipe is */
    boolean consumesSteam(long eut, int duration) {
        return getRecipeSteam(eut, duration) > 0;
    }

    /**
     * Only for a recipe that consumes steam. It needs the steam for its whole run, or a full tank when its run needs
     * more than the tanks hold. The run's steam is what {@link #drawEnergy} takes each tick, for every tick.
     *
     * @param steamStored   the steam the tanks hold, in millibuckets
     * @param steamCapacity the steam the tanks can hold, in millibuckets
     */
    boolean hasSteamForRecipe(long eut, int duration, long steamStored, long steamCapacity) {
        long totalSteam = (long) Math.ceil(eut / conversionRate) * duration;
        // if the required steam is larger than the full tanks, just require the full tanks
        if (steamCapacity < totalSteam) {
            return steamStored >= steamCapacity;
        }
        // otherwise require the full amount of steam for the recipe
        return steamStored >= totalSteam;
    }

    private long getRecipeSteam(long eut, int duration) {
        return (long) (eut * duration / conversionRate);
    }

    /** The drain of the tanks steam is drawn from. */
    @FunctionalInterface
    interface Drain {

        @Nullable
        FluidStack drain(int amount, boolean doDrain);
    }
}

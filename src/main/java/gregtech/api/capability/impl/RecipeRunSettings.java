package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.Nullable;

/**
 * What a machine allows the recipes its logic sets up: the parallel limit, an EU discount and a speed bonus applied
 * before parallels and overclocks, and the overclock limit. Only the overclock limit is saved, under
 * {@value #ALLOW_OVERCLOCKING} and {@value #OVERCLOCK_VOLTAGE}; the rest is set again by the machine.
 * <p>
 * It holds the values only. {@link AbstractRecipeLogic} keeps the public setters and getters, wakes itself when one
 * changes, and computes overclocks through its overridable methods.
 */
final class RecipeRunSettings {

    static final String ALLOW_OVERCLOCKING = "AllowOverclocking";
    static final String OVERCLOCK_VOLTAGE = "OverclockVoltage";

    private int parallelLimit = 1;
    private double euDiscount = -1;
    private double speedBonus = -1;
    private boolean overclockingAllowed = true;
    private long overclockVoltage;

    int getParallelLimit() {
        return parallelLimit;
    }

    void setParallelLimit(int parallelLimit) {
        this.parallelLimit = parallelLimit;
    }

    /** @return the EU/t multiplier, or -1 if there is no discount */
    double getEUDiscount() {
        return euDiscount;
    }

    /** @return whether the discount was taken; one that is not positive is refused with a warning */
    boolean setEUDiscount(double discount) {
        if (discount <= 0) {
            GTLog.logger.warn("Cannot set EU discount for recipe logic to {}, discount must be > 0", discount);
            return false;
        }
        this.euDiscount = discount;
        return true;
    }

    /** @return the duration multiplier, or -1 if there is no bonus */
    double getSpeedBonus() {
        return speedBonus;
    }

    /** @return whether the bonus was taken; one that is not positive is refused with a warning */
    boolean setSpeedBonus(double bonus) {
        if (bonus <= 0) {
            GTLog.logger.warn("Cannot set speed bonus for recipe logic to {}, bonus must be > 0", bonus);
            return false;
        }
        this.speedBonus = bonus;
        return true;
    }

    /** @return whether an EU discount or a speed bonus is set, so recipes must be rebuilt with it */
    boolean hasRecipeBonuses() {
        return euDiscount > 0 || speedBonus > 0;
    }

    /**
     * Rebuilds a recipe with the EU discount and speed bonus. Each is rounded and kept at one or more.
     *
     * @return the rebuilt recipe, or null if the builder refuses it
     */
    @Nullable
    Recipe applyRecipeBonuses(Recipe recipe, @Nullable RecipeMap<?> map) {
        RecipeBuilder<?> builder = new RecipeBuilder<>(recipe, map);
        if (euDiscount > 0) {
            long newEUt = Math.round(recipe.getEUt() * euDiscount);
            if (newEUt <= 0) newEUt = 1;
            builder.EUt(newEUt);
        }
        if (speedBonus > 0) {
            int duration = recipe.getDuration();
            int newDuration = (int) Math.round(duration * speedBonus);
            if (newDuration <= 0) newDuration = 1;
            builder.duration(newDuration);
        }
        return builder.build().getResult();
    }

    boolean isOverclockingAllowed() {
        return overclockingAllowed;
    }

    void setOverclockingAllowed(boolean allowed) {
        this.overclockingAllowed = allowed;
    }

    long getOverclockVoltage() {
        return overclockVoltage;
    }

    void setOverclockVoltage(long voltage) {
        this.overclockVoltage = voltage;
    }

    /** Sets the overclock voltage; overclocking is then allowed only above LV. */
    void limitOverclockVoltage(long voltage) {
        this.overclockVoltage = voltage;
        this.overclockingAllowed = voltage > GTValues.V[GTValues.LV];
    }

    void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean(ALLOW_OVERCLOCKING, overclockingAllowed);
        tag.setLong(OVERCLOCK_VOLTAGE, overclockVoltage);
    }

    void readFromNBT(NBTTagCompound tag) {
        this.overclockingAllowed = tag.getBoolean(ALLOW_OVERCLOCKING);
        this.overclockVoltage = tag.getLong(OVERCLOCK_VOLTAGE);
    }
}

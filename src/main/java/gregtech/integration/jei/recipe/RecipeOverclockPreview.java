package gregtech.integration.jei.recipe;

import gregtech.api.GTValues;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.properties.impl.ComputationProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.recipes.properties.impl.TotalComputationProperty;
import gregtech.api.util.GTUtility;

import org.jetbrains.annotations.Nullable;

/** A single-operation estimate; it never changes the registered recipe or applies machine-specific bonuses. */
final class RecipeOverclockPreview {

    private final long baseEUt;
    private final int baseDuration;
    private final int minimumTier;
    private final @Nullable String unavailableReason;
    private final OCParams params = new OCParams();
    private final OCResult result = new OCResult();

    private int tier;
    private boolean perfect;

    RecipeOverclockPreview(Recipe recipe, boolean generator) {
        this.baseEUt = recipe.getEUt();
        this.baseDuration = recipe.getDuration();
        this.minimumTier = GTUtility.getTierByVoltage(baseEUt);
        this.unavailableReason = findUnavailableReason(recipe, generator);
        reset();
    }

    private static @Nullable String findUnavailableReason(Recipe recipe, boolean generator) {
        if (generator || recipe.getEUt() < 0) return "gregtech.jei.overclock.generator";
        if (recipe.hasProperty(FusionEUToStartProperty.getInstance())) return "gregtech.jei.overclock.fusion";
        if (recipe.hasProperty(ComputationProperty.getInstance()) ||
                recipe.hasProperty(TotalComputationProperty.getInstance())) {
            return "gregtech.jei.overclock.computation";
        }
        if (recipe.propertyStorage().values().stream()
                .anyMatch(property -> property.hideEUt() || property.hideTotalEU() || property.hideDuration())) {
            return "gregtech.jei.overclock.custom";
        }
        if (recipe.getEUt() <= 0 || recipe.getEUt() > GTValues.V[GTValues.MAX] || recipe.getDuration() <= 0) {
            return "gregtech.jei.overclock.invalid";
        }
        return null;
    }

    boolean click(int mouseButton, boolean shift) {
        if (unavailableReason != null) return false;
        switch (mouseButton) {
            case 0 -> this.tier = Math.min(tier + 1, GTValues.MAX);
            case 1 -> this.tier = Math.max(tier - 1, minimumTier);
            case 2 -> {
                reset();
                return true;
            }
            default -> {
                return false;
            }
        }
        this.perfect = shift;
        recalculate();
        return true;
    }

    void reset() {
        this.tier = minimumTier;
        this.perfect = false;
        this.result.init(baseEUt, baseDuration);
    }

    private void recalculate() {
        // Match AbstractRecipeLogic: ULV recipes receive their first overclock at MV.
        int overclocks = tier <= GTValues.LV ? 0 : tier - minimumTier - (minimumTier == GTValues.ULV ? 1 : 0);
        if (overclocks <= 0) {
            this.result.init(baseEUt, baseDuration);
            return;
        }
        this.params.initialize(baseEUt, baseDuration, overclocks);
        OverclockingLogic.standardOC(params, result, GTValues.V[tier],
                perfect ? OverclockingLogic.PERFECT_DURATION_FACTOR : OverclockingLogic.STD_DURATION_FACTOR,
                OverclockingLogic.STD_VOLTAGE_FACTOR);
    }

    long eut() {
        return result.eut();
    }

    int duration() {
        return result.duration();
    }

    int tier() {
        return tier;
    }

    boolean isPerfect() {
        return perfect;
    }

    @Nullable
    String unavailableReason() {
        return unavailableReason;
    }
}

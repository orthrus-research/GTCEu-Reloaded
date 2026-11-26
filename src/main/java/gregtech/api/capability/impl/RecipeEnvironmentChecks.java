package gregtech.api.capability.impl;

import gregtech.api.capability.IMultiblockController;
import gregtech.api.config.GTConfig;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.recipes.properties.impl.CleanroomProperty;
import gregtech.api.recipes.properties.impl.DimensionProperty;

import org.jetbrains.annotations.NotNull;

/**
 * The cleanroom and dimension requirements a recipe may carry, checked against the machine that would run it. The
 * checks only read; each returns the failure it found, or {@link RecipeFailureReason#NONE} when the machine meets the
 * requirement or the recipe has none.
 * <p>
 * {@link AbstractRecipeLogic} records the failure and keeps {@code checkCleanroomRequirement} and
 * {@code checkDimensionRequirement} as the methods a subclass overrides.
 */
final class RecipeEnvironmentChecks {

    private RecipeEnvironmentChecks() {}

    /**
     * A machine meets a cleanroom requirement inside a clean cleanroom of the required type. A multiblock meets every
     * cleanroom requirement when the clean multiblocks setting is on. Only the cleanroom of a machine that can receive
     * one is asked.
     */
    static @NotNull RecipeFailureReason checkCleanroom(@NotNull Recipe recipe, @NotNull MetaTileEntity machine) {
        CleanroomType requiredType = recipe.getProperty(CleanroomProperty.getInstance(), null);
        if (requiredType == null) return RecipeFailureReason.NONE;
        if (!(machine instanceof ICleanroomReceiver receiver)) return RecipeFailureReason.CLEANROOM_MISSING;
        if (GTConfig.get().cleanMultiblocks() && machine instanceof IMultiblockController)
            return RecipeFailureReason.NONE;

        ICleanroomProvider cleanroom = receiver.getCleanroom();
        if (cleanroom == null) return RecipeFailureReason.CLEANROOM_MISSING;
        if (!cleanroom.isClean()) return RecipeFailureReason.CLEANROOM_DIRTY;
        return cleanroom.checkCleanroomType(requiredType) ? RecipeFailureReason.NONE :
                RecipeFailureReason.CLEANROOM_TYPE;
    }

    /** The machine's world is read only for a recipe that names dimensions. */
    static @NotNull RecipeFailureReason checkDimension(@NotNull Recipe recipe, @NotNull MetaTileEntity machine) {
        DimensionProperty.DimensionPropertyList dimensions = recipe.getProperty(DimensionProperty.getInstance(), null);
        if (dimensions == null) return RecipeFailureReason.NONE;
        return dimensions.checkDimension(machine.getWorld().provider.getDimension()) ? RecipeFailureReason.NONE :
                RecipeFailureReason.DIMENSION;
    }
}

package gregtech.api.recipes.ui.impl;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.GTUtility;

import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public class CrackerUnitUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public CrackerUnitUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, false, true, false);
        setFluidSlotOverlay(GTGuiTextures.CRACKING_OVERLAY_1, false);
        setFluidSlotOverlay(GTGuiTextures.CRACKING_OVERLAY_2, true);
        setItemSlotOverlay(GTGuiTextures.CIRCUIT_OVERLAY, false);
        setProgressBar(GTGuiTextures.PROGRESS_BAR_CRACKING, Direction.RIGHT);
    }

    @Override
    public @NotNull RecipeMapLayout createJeiLayout(int importItems, int exportItems, int importFluids,
                                                    int exportFluids) {
        RecipeMapLayout layout = new RecipeMapLayout(WIDTH, HEIGHT);
        if (recipeMap().getMaxInputs() == 1) {
            addSlot(layout, 52, 24, 0, importItems, importFluids, false, false);
        } else {
            int[] grid = determineSlotsGrid(recipeMap().getMaxInputs());
            for (int y = 0; y < grid[1]; y++) {
                for (int x = 0; x < grid[0]; x++) {
                    addSlot(layout, 34 + (x * 18) - (Math.max(0, grid[0] - 2) * 18),
                            24 + (y * 18) - (Math.max(0, grid[1] - 1) * 18),
                            y * grid[0] + x, importItems, importFluids, false, false);
                }
            }
        }

        addInventorySlotGroup(layout, exportItems, exportFluids, true, 0);
        addSlot(layout, 52, 24 + 19 + 18, 0, importItems, importFluids, true, false);
        addSlot(layout, 34, 24 + 19 + 18, 1, importItems, importFluids, true, false);

        Pair<DoubleSupplier, DoubleSupplier> suppliers = GTUtility.createPairedSupplier(200, 41, 0.5);
        layout.progressBar(42, 24 + 18, 21, 19, GTGuiTextures.PROGRESS_BAR_CRACKING_INPUT, Direction.UP,
                suppliers.getLeft(), recipeMap());
        layout.progressBar(78, 23, 20, 20, progressBarTexture(), progressBarDirection(), suppliers.getRight(),
                recipeMap());
        return layout;
    }
}

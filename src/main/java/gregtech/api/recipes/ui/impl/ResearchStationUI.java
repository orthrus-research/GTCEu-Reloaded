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
public class ResearchStationUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public ResearchStationUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, true, false);
        setItemSlotOverlay(GTGuiTextures.SCANNER_OVERLAY, false);
        setItemSlotOverlay(GTGuiTextures.RESEARCH_STATION_OVERLAY, true);
        setProgressBar(GTGuiTextures.PROGRESS_BAR_ARROW, Direction.RIGHT);
    }

    @Override
    public @NotNull RecipeMapLayout createJeiLayout(int importItems, int exportItems, int importFluids,
                                                    int exportFluids) {
        Pair<DoubleSupplier, DoubleSupplier> pairedSuppliers = GTUtility.createPairedSupplier(200, 90, 0.75);
        return new RecipeMapLayout(WIDTH, HEIGHT)
                .image(10, 0, 84, 60, GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE)
                .progressBar(72, 28, 54, 5, GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_1, Direction.RIGHT,
                        pairedSuppliers.getLeft(), null)
                .progressBar(119, 32, 10, 18, GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_2, Direction.DOWN,
                        pairedSuppliers.getRight(), null)
                .slot(true, false, 0, 115, 50, GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY)
                .slot(false, false, 1, 43, 21, GTGuiTextures.SLOT, GTGuiTextures.SCANNER_OVERLAY)
                .slot(false, false, 0, 97, 21, GTGuiTextures.SLOT, GTGuiTextures.RESEARCH_STATION_OVERLAY);
    }
}

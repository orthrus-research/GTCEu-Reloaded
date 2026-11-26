package gregtech.api.recipes.ui.impl;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.function.impl.TimedProgressSupplier;

import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class CokeOvenUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /**
     * @param recipeMap the recipemap corresponding to this ui
     */
    public CokeOvenUI(@NotNull R recipeMap) {
        super(recipeMap, false, false, false, false, false);
    }

    @Override
    public @NotNull RecipeMapLayout createJeiLayout(int importItems, int exportItems, int importFluids,
                                                    int exportFluids) {
        RecipeMapLayout layout = new RecipeMapLayout(WIDTH, 100)
                .progressBar(70, 19, 36, 18, GTGuiTextures.PROGRESS_BAR_COKE_OVEN, Direction.RIGHT,
                        new TimedProgressSupplier(VIEWER_PROGRESS_TICKS, 36, false), null);
        addSlot(layout, 52, 10, 0, importItems, importFluids, false, false);
        addSlot(layout, 106, 10, 0, exportItems, exportFluids, false, true);
        addSlot(layout, 106, 28, 0, exportItems, exportFluids, true, true);
        return layout;
    }
}

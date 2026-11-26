package gregtech.api.recipes.ui.impl;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class FormingPressUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public FormingPressUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, true, false);
        setProgressBar(GTGuiTextures.PROGRESS_BAR_COMPRESS, Direction.RIGHT);
    }

    @Override
    protected void addSlot(@NotNull RecipeMapLayout layout, int x, int y, int slotIndex, int itemCount,
                           int fluidCount, boolean isFluid, boolean isOutputs) {
        UITexture overlay = null;
        if (isOutputs)
            overlay = GTGuiTextures.PRESS_OVERLAY_3;
        else if (slotIndex == 0 || slotIndex == 3)
            overlay = GTGuiTextures.PRESS_OVERLAY_2;
        else if (slotIndex == 1 || slotIndex == 4)
            overlay = GTGuiTextures.PRESS_OVERLAY_4;
        else if (slotIndex == 2 || slotIndex == 5)
            overlay = GTGuiTextures.PRESS_OVERLAY_1;

        if (overlay == null) {
            layout.slot(isOutputs, false, slotIndex, x, y);
        } else {
            layout.slot(isOutputs, false, slotIndex, x, y, GTGuiTextures.SLOT, overlay);
        }
    }
}

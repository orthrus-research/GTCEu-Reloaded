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
public final class AssemblyLineUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /**
     * @param recipeMap the recipemap corresponding to this ui
     */
    public AssemblyLineUI(@NotNull R recipeMap) {
        super(recipeMap, false, false, false, false, false);
        setProgressBar(GTGuiTextures.PROGRESS_BAR_ARROW, Direction.RIGHT);
    }

    @Override
    public @NotNull RecipeMapLayout createJeiLayout(int importItems, int exportItems, int importFluids,
                                                    int exportFluids) {
        RecipeMapLayout layout = new RecipeMapLayout(WIDTH, 176)
                .progressBar(80, 1, 54, 72, GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE, Direction.RIGHT,
                        new TimedProgressSupplier(VIEWER_PROGRESS_TICKS, 54, false), null)
                .progressBar(138, 19, 10, 18, GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE_ARROW, Direction.UP,
                        new TimedProgressSupplier(VIEWER_PROGRESS_TICKS, 18, false), null);
        addInventorySlotGroup(layout, importItems, importFluids, false, 0);
        addInventorySlotGroup(layout, exportItems, exportFluids, true, 0);
        return layout;
    }

    @Override
    protected void addInventorySlotGroup(@NotNull RecipeMapLayout layout, int itemCount, int fluidCount,
                                         boolean isOutputs, int yOffset) {
        int startInputsX = 80 - 4 * 18;
        int startInputsY = 37 - 2 * 18;

        if (!isOutputs) {
            // Data Slot
            layout.slot(false, false, 16, startInputsX + 18 * 7, 1 + 18 * 2, GTGuiTextures.SLOT,
                    GTGuiTextures.DATA_ORB_OVERLAY);

            // item input slots
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int slotIndex = i * 4 + j;
                    addSlot(layout, startInputsX + 18 * j, startInputsY + 18 * i, slotIndex, itemCount, fluidCount,
                            false, false);
                }
            }

            // fluid slots
            int startFluidX = startInputsX + 18 * 5;
            for (int i = 0; i < 4; i++) {
                addSlot(layout, startFluidX, startInputsY + 18 * i, i, itemCount, fluidCount, true, false);
            }
        } else {
            // output slot
            addSlot(layout, startInputsX + 18 * 7, 1, 0, itemCount, fluidCount, false, true);
        }
    }
}

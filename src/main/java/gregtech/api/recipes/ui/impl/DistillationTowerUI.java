package gregtech.api.recipes.ui.impl;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.function.impl.TimedProgressSupplier;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import org.jetbrains.annotations.NotNull;

public class DistillationTowerUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /** The output tanks the beaker overlays are drawn under, which cycle through three beakers. */
    private static final int BEAKER_TANKS = 12;

    public DistillationTowerUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, false, false);
    }

    @Override
    protected void addSlot(@NotNull RecipeMapLayout layout, int x, int y, int slotIndex, int itemCount,
                           int fluidCount, boolean isFluid, boolean isOutputs) {
        if (isFluid) {
            UITexture overlay;
            if (!isOutputs)
                overlay = GTGuiTextures.BEAKER_OVERLAY_1;
            else if (slotIndex >= BEAKER_TANKS)
                overlay = null;
            else if (slotIndex % 3 == 0)
                overlay = GTGuiTextures.BEAKER_OVERLAY_2;
            else if (slotIndex % 3 == 1)
                overlay = GTGuiTextures.BEAKER_OVERLAY_3;
            else
                overlay = GTGuiTextures.BEAKER_OVERLAY_4;

            if (overlay == null) {
                layout.slot(isOutputs, true, slotIndex, x, y);
            } else {
                layout.slot(isOutputs, true, slotIndex, x, y, GTGuiTextures.FLUID_SLOT, overlay);
            }
        } else {
            layout.slot(isOutputs, false, slotIndex, x, y, GTGuiTextures.SLOT, GTGuiTextures.DUST_OVERLAY);
        }
    }

    @Override
    public @NotNull RecipeMapLayout createJeiLayout(int importItems, int exportItems, int importFluids,
                                                    int exportFluids) {
        RecipeMapLayout layout = new RecipeMapLayout(WIDTH, HEIGHT)
                .progressBar(47, 8, 66, 58, GTGuiTextures.PROGRESS_BAR_DISTILLATION_TOWER, Direction.RIGHT,
                        new TimedProgressSupplier(VIEWER_PROGRESS_TICKS, 66, false), null);
        addInventorySlotGroup(layout, importItems, importFluids, false, 9);
        addInventorySlotGroup(layout, exportItems, exportFluids, true, 9);
        addSpecialTexture(layout);
        return layout;
    }

    @Override
    protected void addInventorySlotGroup(@NotNull RecipeMapLayout layout, int itemCount, int fluidCount,
                                         boolean isOutputs, int yOffset) {
        int itemInputsCount = itemCount;
        int fluidInputsCount = fluidCount;
        boolean invertFluids = false;
        if (itemInputsCount == 0) {
            int tmp = itemInputsCount;
            itemInputsCount = fluidInputsCount;
            fluidInputsCount = tmp;
            invertFluids = true;
        }
        int[] inputSlotGrid = RecipeMapUI.determineSlotsGrid(itemInputsCount);
        int itemSlotsToLeft = inputSlotGrid[0];
        int itemSlotsToDown = inputSlotGrid[1];
        int startInputsX = isOutputs ? 104 : 68 - itemSlotsToLeft * 18;
        int startInputsY = 55 - (int) (itemSlotsToDown / 2.0 * 18) + yOffset;
        boolean wasGroupOutput = itemCount + fluidCount == 12;
        if (wasGroupOutput && isOutputs) startInputsY -= 9;
        if (itemCount == 6 && fluidCount == 2 && !isOutputs) startInputsY -= 9;
        if (!isOutputs) {
            addSlot(layout, 40, startInputsY + (itemSlotsToDown - 1) * 18 - 18, 0, itemCount, fluidCount,
                    invertFluids, false);
        } else {
            addSlot(layout, 94, startInputsY + (itemSlotsToDown - 1) * 18, 0, itemCount, fluidCount, invertFluids,
                    true);
        }

        if (wasGroupOutput) startInputsY += 2;

        if (!isOutputs) return;

        if (!invertFluids) {
            startInputsY -= 18;
            startInputsX += 9;
        }

        if (fluidInputsCount > 0 || invertFluids) {
            int startSpecY = startInputsY + itemSlotsToDown * 18;
            for (int i = 0; i < fluidInputsCount; i++) {
                int x = startInputsX + 18 * (i % 3);
                int y = startSpecY - (i / 3) * 18;
                addSlot(layout, x, y, i, itemCount, fluidCount, true, true);
            }
        }
    }
}

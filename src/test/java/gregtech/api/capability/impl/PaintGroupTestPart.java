package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;

class PaintGroupTestPart extends DualHatchTestPart {

    RecipeMapMultiblockController owner;

    PaintGroupTestPart(boolean output, int color) {
        super(GTValues.LuV, output);
        setPaintingColor(color);
    }

    @Override
    public MultiblockControllerBase getController() {
        return owner;
    }

    @Override
    public boolean isAttachedToMultiBlock() {
        return owner != null;
    }
}

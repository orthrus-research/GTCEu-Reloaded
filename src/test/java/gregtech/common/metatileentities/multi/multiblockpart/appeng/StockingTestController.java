package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static gregtech.api.util.GTUtility.gregtechId;

final class StockingTestController extends RecipeMapMultiblockController {

    final List<Object> inputs = new ArrayList<>();
    final List<Object> fluids = new ArrayList<>();
    boolean distinct;

    StockingTestController() {
        super(gregtechId("stocking_test_controller"), RecipeMaps.BLAST_RECIPES);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        if (ability == MultiblockAbility.IMPORT_ITEMS) return (List<T>) inputs;
        if (ability == MultiblockAbility.IMPORT_FLUIDS) return (List<T>) fluids;
        return Collections.emptyList();
    }

    @Override
    public boolean isDistinct() {
        return distinct;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new StockingTestController();
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return null;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart part) {
        return null;
    }
}

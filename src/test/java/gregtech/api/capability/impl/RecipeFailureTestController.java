package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.world.World;

class RecipeFailureTestController extends RecipeMapMultiblockController {

    long tick = 1;
    boolean formed = true;
    boolean mufflerFree = true;
    boolean distinct;
    int maintenanceProblems;
    int mufflerChecks;

    RecipeFailureTestController(RecipeMap<?> map) {
        super(GTUtility.gregtechId("failure_controller_test"), map);
    }

    void install(MultiblockRecipeLogic logic) {
        this.recipeMapWorkable = logic;
    }

    void runFormed() {
        updateFormedValid();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeFailureTestController(recipeMap);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return null;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart part) {
        return null;
    }

    @Override
    public World getWorld() {
        return DummyWorld.INSTANCE;
    }

    @Override
    public long getOffsetTimer() {
        return tick;
    }

    @Override
    public boolean isStructureFormed() {
        return formed;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean isMufflerFaceFree() {
        mufflerChecks++;
        return mufflerFree;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return true;
    }

    @Override
    public int getNumMaintenanceProblems() {
        return maintenanceProblems;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public boolean isDistinct() {
        return distinct;
    }

    @Override
    public void markDirty() {}
}

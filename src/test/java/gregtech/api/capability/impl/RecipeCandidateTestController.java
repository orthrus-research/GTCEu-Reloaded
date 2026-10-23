package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.world.World;

import java.util.function.Predicate;

final class RecipeCandidateTestController extends RecipeMapMultiblockController {

    long tick;
    int checks;
    int commits;
    boolean finalAllowed = true;
    Predicate<Recipe> eligibility = recipe -> true;

    RecipeCandidateTestController(RecipeMap<?> map) {
        super(GTUtility.gregtechId("candidate_distinct_test"), map);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeCandidateTestController(recipeMap);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return null;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
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
    public boolean isRecipeEligible(Recipe recipe) {
        return eligibility.test(recipe);
    }

    @Override
    public boolean checkRecipe(Recipe recipe, boolean consumeIfSuccess) {
        if (consumeIfSuccess) commits++;
        else checks++;
        return finalAllowed;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public void markDirty() {}
}

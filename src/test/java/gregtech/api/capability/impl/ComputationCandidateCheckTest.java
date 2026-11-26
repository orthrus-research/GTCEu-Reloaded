package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalComputationReceiver;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.ICubeRenderer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ComputationCandidateCheckTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void computationLogicAndItsSubclassesKeepCandidateChecksOff() {
        ComputationTestController controller = new ComputationTestController(RecipeCandidateSelectionTest.map());
        ComputationRecipeLogic logic = new ComputationRecipeLogic(controller,
                ComputationRecipeLogic.ComputationType.SPORADIC);
        assertFalse(logic.supportsRecipeCandidateEligibility());
        ComputationRecipeLogic subclass = new ComputationRecipeLogic(controller,
                ComputationRecipeLogic.ComputationType.STEADY) {};
        assertFalse(subclass.supportsRecipeCandidateEligibility());
    }

    private static final class ComputationTestController extends RecipeMapMultiblockController
                                                         implements IOpticalComputationReceiver {

        ComputationTestController(RecipeMap<?> map) {
            super(GTUtility.gregtechId("candidate_computation_test"), map);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new ComputationTestController(recipeMap);
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
        public IOpticalComputationProvider getComputationProvider() {
            return null;
        }
    }
}

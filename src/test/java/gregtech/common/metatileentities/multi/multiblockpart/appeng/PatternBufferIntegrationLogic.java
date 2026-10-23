package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.impl.MultiblockRecipeLogic;

final class PatternBufferIntegrationLogic extends MultiblockRecipeLogic {

    PatternBufferIntegrationLogic(PatternBufferIntegrationController controller) {
        super(controller);
    }

    void search() {
        trySearchNewRecipe();
    }

    boolean maySearch() {
        return shouldSearchForRecipes();
    }

    void finish() {
        tryCompleteRecipe();
    }
}

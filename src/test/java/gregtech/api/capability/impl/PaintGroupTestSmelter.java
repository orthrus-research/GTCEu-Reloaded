package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiSmelter;

import java.util.Collections;
import java.util.List;

/** Real multi-smelter logic; only formation, energy supply and physical ability ownership are supplied in memory. */
final class PaintGroupTestSmelter extends MetaTileEntityMultiSmelter {

    private PaintGroupTestController abilities;

    PaintGroupTestSmelter(PaintGroupTestPart... parts) {
        super(GTUtility.gregtechId("paint_test_smelter"));
        IdleRecipeTestMachine.attachHolder(this);
        this.abilities = new PaintGroupTestController(RecipeMaps.FURNACE_RECIPES, parts);
        for (PaintGroupTestPart part : parts) part.owner = this;
        initializeAbilities();
        this.heatingCoilLevel = 1;
        this.heatingCoilDiscount = 1;
        this.energyContainer = new EnergyContainerHandler(this, 100_000, 32, 4, 0, 0);
        energyContainer.addEnergy(100_000);
        recipeMapWorkable.setAllowOverclocking(false);
        setPaintGroupingEnabled(true);
    }

    @Override
    public boolean canUseRecipeInputViews() {
        return true;
    }

    @Override
    public boolean isStructureFormed() {
        return true;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }

    @Override
    public void markDirty() {}

    @Override
    public List<IMultiblockPart> getMultiblockParts() {
        return abilities == null ? Collections.emptyList() : abilities.getMultiblockParts();
    }

    @Override
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        return abilities == null ? Collections.emptyList() : abilities.getAbilities(ability);
    }

    @Override
    public <T> IMultiblockPart getAbilityOwner(MultiblockAbility<T> ability, T instance) {
        return abilities.getAbilityOwner(ability, instance);
    }
}

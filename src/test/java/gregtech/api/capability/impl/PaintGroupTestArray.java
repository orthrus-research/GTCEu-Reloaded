package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collections;
import java.util.List;

/** Uses actual machine discovery and ProcessingArrayWorkable, including its installed-voltage and parallel limits. */
final class PaintGroupTestArray extends MetaTileEntityProcessingArray {

    private PaintGroupTestController abilities;
    private final ItemStackHandler machineSlot = new ItemStackHandler(1);

    PaintGroupTestArray(RecipeMap<?> map, ItemStack installed, PaintGroupTestPart... parts) {
        super(GTUtility.gregtechId("paint_test_array"), 0);
        IdleRecipeTestMachine.attachHolder(this);
        this.abilities = new PaintGroupTestController(map, parts);
        for (PaintGroupTestPart part : parts) part.owner = this;
        initializeAbilities();
        this.energyContainer = new EnergyContainerHandler(this, 100_000, 128, 4, 0, 0);
        energyContainer.addEnergy(100_000);
        machineSlot.setStackInSlot(0, installed);
        ((ProcessingArrayWorkable) recipeMapWorkable).findMachineStack();
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
    @SuppressWarnings("unchecked")
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        if (ability == MultiblockAbility.MACHINE_HATCH && machineSlot != null)
            return (List<T>) Collections.singletonList(machineSlot);
        return abilities == null ? Collections.emptyList() : abilities.getAbilities(ability);
    }

    @Override
    public <T> IMultiblockPart getAbilityOwner(MultiblockAbility<T> ability, T instance) {
        return abilities.getAbilityOwner(ability, instance);
    }
}

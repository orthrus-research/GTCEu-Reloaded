package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.RecipeMap;

import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DualInputTestController extends RecipeFailureTestController {

    private final Map<MultiblockAbility<?>, AbilityInstances> abilities = new HashMap<>();

    DualInputTestController(RecipeMap<?> map, FluidTank shared, DualHatchTestPart... parts) {
        super(map);
        IdleRecipeTestMachine.attachHolder(this);
        for (DualHatchTestPart part : parts) {
            for (var ability : part.getAbilities()) {
                part.registerAbilities(abilities.computeIfAbsent(ability, AbilityInstances::new));
            }
            part.getRecipeHandler().addNotifiableMetaTileEntity(this);
        }
        abilities.computeIfAbsent(MultiblockAbility.IMPORT_FLUIDS, AbilityInstances::new).add(shared);
        abilities.computeIfAbsent(MultiblockAbility.EXPORT_ITEMS, AbilityInstances::new).add(new ItemStackHandler(2));
        abilities.computeIfAbsent(MultiblockAbility.EXPORT_FLUIDS, AbilityInstances::new).add(new FluidTank(1000));
        initializeAbilities();
        this.energyContainer = new EnergyContainerHandler(this, 100_000, 32, 1, 0, 0);
        this.energyContainer.addEnergy(100_000);
        recipeMapWorkable.setAllowOverclocking(false);
    }

    @Override
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        return abilities == null || !abilities.containsKey(ability) ? Collections.emptyList() :
                abilities.get(ability).cast();
    }

    @Override
    public void setDistinct(boolean value) {
        this.distinct = value;
        super.setDistinct(value);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }
}

package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class PaintGroupTestController extends RecipeFailureTestController {

    private final Map<MultiblockAbility<?>, AbilityInstances> abilities = new HashMap<>();
    private final Map<Object, IMultiblockPart> owners = new IdentityHashMap<>();
    private final List<IMultiblockPart> parts = new ArrayList<>();
    Runnable beforeStart;
    boolean voidItems;
    boolean voidFluids;
    net.minecraft.world.World environment;

    PaintGroupTestController(RecipeMap<?> map, PaintGroupTestPart... sources) {
        super(map);
        IdleRecipeTestMachine.attachHolder(this);
        for (PaintGroupTestPart source : sources) {
            parts.add(source);
            source.owner = this;
            for (var ability : source.getAbilities()) {
                AbilityInstances claimed = new AbilityInstances(ability);
                source.registerAbilities(claimed);
                AbilityInstances all = abilities.computeIfAbsent(ability, AbilityInstances::new);
                for (Object instance : claimed) {
                    all.add(instance);
                    owners.put(instance, source);
                }
            }
            source.getRecipeHandler().addNotifiableMetaTileEntity(this);
        }
        initializeAbilities();
        this.energyContainer = new EnergyContainerHandler(this, 100_000, 32, 1, 0, 0);
        this.energyContainer.addEnergy(100_000);
        recipeMapWorkable.setAllowOverclocking(false);
    }

    void forgetOwner(Object instance) {
        owners.remove(instance);
        onRecipeInputViewsChanged();
    }

    @Override
    public boolean canUseRecipeInputViews() {
        return true;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public net.minecraft.world.World getWorld() {
        return environment == null ? super.getWorld() : environment;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return voidItems;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return voidFluids;
    }

    @Override
    public List<IMultiblockPart> getMultiblockParts() {
        return Collections.unmodifiableList(parts);
    }

    @Override
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        return abilities == null || !abilities.containsKey(ability) ? Collections.emptyList() :
                abilities.get(ability).cast();
    }

    @Override
    public <T> IMultiblockPart getAbilityOwner(MultiblockAbility<T> ability, T instance) {
        return owners.get(instance);
    }

    @Override
    public void setDistinct(boolean value) {
        this.distinct = value;
        super.setDistinct(value);
    }

    @Override
    public boolean checkRecipe(Recipe recipe, boolean consumeIfSuccess) {
        if (!consumeIfSuccess && beforeStart != null) {
            Runnable action = beforeStart;
            beforeStart = null;
            action.run();
        }
        return true;
    }
}

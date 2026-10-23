package gregtech.api.capability.impl;

import gregtech.api.recipes.modifiers.IRecipeModifierProvider;
import gregtech.api.recipes.modifiers.RecipeModifierList;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandlerModifiable;

final class ModifierRuntimeMachine extends PendingOutputTestMachine implements IRecipeModifierProvider {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();
    RecipeModifierList modifiers = RecipeModifierList.EMPTY;
    boolean voidItems;
    IItemHandlerModifiable bulkInputs;

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public BlockPos getPos() {
        return new BlockPos(18, 64, -7);
    }

    @Override
    public RecipeModifierList getRecipeModifiers() {
        return modifiers;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return voidItems;
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return bulkInputs == null ? super.getImportItems() : bulkInputs;
    }
}

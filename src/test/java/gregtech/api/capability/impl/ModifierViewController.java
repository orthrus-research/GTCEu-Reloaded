package gregtech.api.capability.impl;

import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.modifiers.IRecipeModifierProvider;
import gregtech.api.recipes.modifiers.RecipeModifierList;

import net.minecraft.util.math.BlockPos;

final class ModifierViewController extends PaintGroupTestController implements IRecipeModifierProvider {

    RecipeModifierList modifiers = RecipeModifierList.EMPTY;
    int providerReads;

    ModifierViewController(RecipeMap<?> map, PaintGroupTestPart... parts) {
        super(map, parts);
        this.environment = new ConditionRuntimeWorld();
    }

    @Override
    public BlockPos getPos() {
        return new BlockPos(31, 64, -9);
    }

    @Override
    public RecipeModifierList getRecipeModifiers() {
        providerReads++;
        return modifiers;
    }
}

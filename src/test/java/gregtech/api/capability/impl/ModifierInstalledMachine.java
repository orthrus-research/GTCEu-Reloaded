package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.modifiers.IRecipeModifierProvider;
import gregtech.api.recipes.modifiers.RecipeModifierList;

import net.minecraft.util.ResourceLocation;

final class ModifierInstalledMachine extends SimpleMachineMetaTileEntity implements IRecipeModifierProvider {

    private final RecipeMap<?> map;

    ModifierInstalledMachine(ResourceLocation id, RecipeMap<?> map) {
        super(id, map, null, 1, true);
        this.map = map;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tile) {
        return new ModifierInstalledMachine(metaTileEntityId, map);
    }

    @Override
    public RecipeModifierList getRecipeModifiers() {
        throw new AssertionError("Installed PA provider must never be consulted");
    }
}

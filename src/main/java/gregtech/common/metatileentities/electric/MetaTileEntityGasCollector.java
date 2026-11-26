package gregtech.common.metatileentities.electric;

import gregtech.api.capability.impl.RecipeLogicEnergy;
import gregtech.api.configuration.ConfigurationAdapter;
import gregtech.api.items.toolitem.ToolAction;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

public class MetaTileEntityGasCollector extends SimpleMachineMetaTileEntity {

    public MetaTileEntityGasCollector(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, ICubeRenderer renderer,
                                      int tier, boolean hasFrontFacing,
                                      Function<Integer, Integer> tankScalingFunction) {
        super(metaTileEntityId, recipeMap, renderer, tier, hasFrontFacing, tankScalingFunction);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityGasCollector(this.metaTileEntityId, RecipeMaps.GAS_COLLECTOR_RECIPES,
                Textures.GAS_COLLECTOR_OVERLAY, this.getTier(), this.hasFrontFacing(), this.getTankScalingFunction());
    }

    @Override
    protected RecipeLogicEnergy createWorkable(RecipeMap<?> recipeMap) {
        return new RecipeLogicEnergy(this, recipeMap, () -> energyContainer);
    }

    /** Keeps its recipe logic ticking while idle, as before. */
    @Override
    public boolean supportsIdleRecipeScheduling() {
        return false;
    }

    /** Keeps auto-output probing on every attempt, as before. */
    @Override
    protected boolean supportsAutoOutputScheduling() {
        return false;
    }

    /** Keeps this machine without a configuration card, as before; the simple-machine card would fit it. */
    @Override
    public @Nullable ConfigurationAdapter getConfigurationAdapter() {
        return null;
    }

    /** Keeps this machine without tool hints, as before; its tool clicks are the simple machine's. */
    @Override
    public @Nullable ToolAction getToolActionHint(Set<String> toolClasses, boolean sneaking, EnumFacing side) {
        return null;
    }
}

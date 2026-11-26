package gregtech.common.metatileentities.electric;

import gregtech.api.capability.IEnergyContainer;
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

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public class MetaTileEntityRockBreaker extends SimpleMachineMetaTileEntity {

    private boolean hasValidFluids;

    public MetaTileEntityRockBreaker(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, ICubeRenderer renderer,
                                     int tier) {
        super(metaTileEntityId, recipeMap, renderer, tier, true);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityRockBreaker(metaTileEntityId, RecipeMaps.ROCK_BREAKER_RECIPES,
                Textures.ROCK_BREAKER_OVERLAY, getTier());
    }

    @Override
    protected RecipeLogicEnergy createWorkable(RecipeMap<?> recipeMap) {
        return new RockBreakerRecipeLogic(this, RecipeMaps.ROCK_BREAKER_RECIPES, () -> energyContainer);
    }

    /** Its recipes depend on the fluids beside it, not only on its inputs, so its logic keeps ticking while idle. */
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

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        checkAdjacentFluids();
    }

    private void checkAdjacentFluids() {
        if (getWorld() == null) {
            hasValidFluids = true;
            return;
        }
        if (getWorld().isRemote) {
            hasValidFluids = false;
            return;
        }
        boolean hasLava = false;
        boolean hasWater = false;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (hasLava && hasWater) {
                break;
            }

            if (side == frontFacing || side.getAxis().isVertical()) {
                continue;
            }

            Block block = getWorld().getBlockState(getPos().offset(side)).getBlock();
            if (block == Blocks.FLOWING_LAVA || block == Blocks.LAVA) {
                hasLava = true;
            } else if (block == Blocks.FLOWING_WATER || block == Blocks.WATER) {
                hasWater = true;
            }
        }
        this.hasValidFluids = hasLava && hasWater;
    }

    @Override
    public void addNotifiedInput(Object input) {
        super.addNotifiedInput(input);
        onNeighborChanged();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("hasValidFluids", hasValidFluids);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("hasValidFluids")) {
            this.hasValidFluids = data.getBoolean("hasValidFluids");
        }
    }

    protected class RockBreakerRecipeLogic extends RecipeLogicEnergy {

        public RockBreakerRecipeLogic(MetaTileEntity metaTileEntity, RecipeMap<?> recipeMap,
                                      Supplier<IEnergyContainer> energyContainer) {
            super(metaTileEntity, recipeMap, energyContainer);
        }

        @Override
        protected boolean shouldSearchForRecipes() {
            return hasValidFluids && super.shouldSearchForRecipes();
        }

        /** The adjacent fluids change without an input notification, so this logic keeps ticking while idle. */
        @Override
        protected boolean supportsIdleRecipeScheduling() {
            return false;
        }
    }

    @Override
    public boolean getIsWeatherOrTerrainResistant() {
        return true;
    }
}

package gregtech.common.metatileentities.steam;

import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.capability.impl.RecipeLogicSteam;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

public class SteamRockBreaker extends SteamMetaTileEntity {

    private boolean hasValidFluids;

    public SteamRockBreaker(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, RecipeMaps.ROCK_BREAKER_RECIPES, Textures.ROCK_BREAKER_OVERLAY, isHighPressure);
        this.workableHandler = new SteamRockBreakerRecipeLogic(this,
                workableHandler.getRecipeMap(), isHighPressure, steamFluidTank, 1.0);
        if (getWorld() != null && !getWorld().isRemote) {
            checkAdjacentFluids();
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamRockBreaker(metaTileEntityId, isHighPressure);
    }

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        checkAdjacentFluids();
    }

    private void checkAdjacentFluids() {
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
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, 4, this, true);
    }

    @Override
    protected @NotNull RecipeMapLayout createScreenLayout(@NotNull DoubleSupplier progress) {
        UITexture slot = GTGuiTextures.SLOT_STEAM.get(isHighPressure);
        UITexture output = GTGuiTextures.CRUSHED_ORE_OVERLAY_STEAM.get(isHighPressure);
        return new RecipeMapLayout(RecipeMapUI.WIDTH, RecipeMapUI.HEIGHT)
                .slot(false, false, 0, 53, 34, slot, GTGuiTextures.DUST_OVERLAY_STEAM.get(isHighPressure))
                .progressBar(79, 35, 21, 18, GTGuiTextures.PROGRESS_BAR_MACERATE_STEAM.get(isHighPressure),
                        Direction.RIGHT, progress, workableHandler.getRecipeMap())
                .slot(true, false, 0, 107, 25, slot, output)
                .slot(true, false, 1, 125, 25, slot, output)
                .slot(true, false, 2, 107, 43, slot, output)
                .slot(true, false, 3, 125, 43, slot, output);
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

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (isActive()) {
            VanillaParticleEffects.defaultFrontEffect(this, 0.4F, EnumParticleTypes.SMOKE_NORMAL);
        }
    }

    protected class SteamRockBreakerRecipeLogic extends RecipeLogicSteam {

        public SteamRockBreakerRecipeLogic(MetaTileEntity tileEntity, RecipeMap<?> recipeMap, boolean isHighPressure,
                                           IFluidTank steamFluidTank, double conversionRate) {
            super(tileEntity, recipeMap, isHighPressure, steamFluidTank, conversionRate);
        }

        @Override
        protected boolean shouldSearchForRecipes() {
            return hasValidFluids && super.shouldSearchForRecipes();
        }
    }
}

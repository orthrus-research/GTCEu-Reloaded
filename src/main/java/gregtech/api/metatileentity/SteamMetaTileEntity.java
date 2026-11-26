package gregtech.api.metatileentity;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.capability.impl.FilteredFluidHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.RecipeLogicSteam;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.RecipeFailureWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.util.GTUtility;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleSidedCubeRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

public abstract class SteamMetaTileEntity extends MetaTileEntity {

    protected static final int STEAM_CAPACITY = 16000;

    protected final boolean isHighPressure;
    protected final ICubeRenderer renderer;
    protected RecipeLogicSteam workableHandler;
    protected FluidTank steamFluidTank;

    public SteamMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, ICubeRenderer renderer,
                               boolean isHighPressure) {
        super(metaTileEntityId);
        this.workableHandler = new RecipeLogicSteam(this,
                recipeMap, isHighPressure, steamFluidTank, 1.0);
        this.isHighPressure = isHighPressure;
        this.renderer = renderer;
    }

    @Override
    public boolean isActive() {
        return workableHandler.isActive() && workableHandler.isWorkingEnabled();
    }

    @SideOnly(Side.CLIENT)
    protected SimpleSidedCubeRenderer getBaseRenderer() {
        if (isHighPressure) {
            if (isBrickedCasing()) {
                return Textures.STEAM_BRICKED_CASING_STEEL;
            } else {
                return Textures.STEAM_CASING_STEEL;
            }
        } else {
            if (isBrickedCasing()) {
                return Textures.STEAM_BRICKED_CASING_BRONZE;
            } else {
                return Textures.STEAM_CASING_BRONZE;
            }
        }
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            EnumFacing currentVentingSide = workableHandler.getVentingSide();
            if (currentVentingSide == facing ||
                    getFrontFacing() == facing)
                return false;
            workableHandler.setVentingSide(facing);
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(getBaseRenderer().getParticleSprite(), getPaintingColorForRendering());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        IVertexOperation[] colouredPipeline = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        getBaseRenderer().render(renderState, translation, colouredPipeline);
        renderer.renderOrientedState(renderState, translation, pipeline, getFrontFacing(), workableHandler.isActive(),
                workableHandler.isWorkingEnabled());
        Textures.STEAM_VENT_OVERLAY.renderSided(workableHandler.getVentingSide(), renderState,
                RenderUtil.adjustTrans(translation, workableHandler.getVentingSide(), 2), pipeline);
    }

    protected boolean isBrickedCasing() {
        return false;
    }

    @Override
    public FluidTankList createImportFluidHandler() {
        this.steamFluidTank = new FilteredFluidHandler(STEAM_CAPACITY).setFilter(CommonFluidFilters.STEAM);
        return new FluidTankList(false, steamFluidTank);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public GTGuiTheme getUITheme() {
        return isHighPressure ? GTGuiTheme.STEEL : GTGuiTheme.BRONZE;
    }

    /**
     * The screen, in the bronze or steel theme: the machine's slots, progress bar and images from its layout, the
     * missing steam indicator and the player's inventory.
     */
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        DoubleSyncValue progress = new DoubleSyncValue(workableHandler::getProgressPercent);
        syncManager.syncValue("recipe_progress", progress);
        RecipeMapLayout layout = createScreenLayout(progress::getDoubleValue);

        ModularPanel panel = GTGuis.createPanel(this, layout.getWidth(), layout.getHeight())
                .child(IKey.lang(getMetaFullName()).asWidget().pos(6, 6));
        layout.addWidgets(panel, importItems, exportItems, importFluids, exportFluids);
        return panel.child(RecipeFailureWidget.create(syncManager, workableHandler::getRecipeFailure,
                GTGuiTextures.INDICATOR_NO_STEAM.get(isHighPressure)).pos(79, 42))
                .child(SlotGroupWidget.playerInventory(false).pos(7, 84));
    }

    /**
     * Lay out this machine's screen: its slots, progress bar and images, in its bronze or steel textures.
     *
     * @param progress the machine's progress, from 0 to 1
     * @return the layout
     */
    protected abstract @NotNull RecipeMapLayout createScreenLayout(@NotNull DoubleSupplier progress);

    @Override
    public SoundEvent getSound() {
        return Objects.requireNonNull(workableHandler.getRecipeMap()).getSound();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (this.isActive()) {
            EnumParticleTypes smokeParticle = isHighPressure ? EnumParticleTypes.SMOKE_LARGE :
                    EnumParticleTypes.SMOKE_NORMAL;
            VanillaParticleEffects.defaultFrontEffect(this, smokeParticle, EnumParticleTypes.FLAME);

            if (ConfigHolder.machines.machineSounds && GTValues.RNG.nextDouble() < 0.1) {
                BlockPos pos = getPos();
                getWorld().playSound(pos.getX(), pos.getY(), pos.getZ(),
                        SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
            }
        }
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.reset"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}

package gregtech.common.metatileentities.steam;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.client.particle.IMachineParticleEffect;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.common.ConfigHolder;

import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

/**
 * A steam single-block machine, bronze or at high pressure steel, whose recipe map, overlay, casing, screen and
 * particle effects come from a {@link SteamMachineDefinition}.
 * <p>
 * GTCEu's own steam machines keep their classes, such as {@link SteamFurnace}, so that code naming them keeps
 * working; each of those classes only holds its definition.
 */
public class SimpleSteamMachine extends SteamMetaTileEntity {

    private final SteamMachineDefinition definition;

    public SimpleSteamMachine(@NotNull ResourceLocation metaTileEntityId, @NotNull SteamMachineDefinition definition,
                              boolean isHighPressure) {
        // assigned before the super constructor, which creates the inventory the definition sizes
        this.definition = definition;
        super(metaTileEntityId, definition.recipeMap, definition.overlay, isHighPressure);
    }

    /**
     * The smoke and flame of a machine that burns, from its front, with a furnace's crackle. The smoke is large at
     * high pressure.
     *
     * @param sidewaysOffset how far along the front the particles are moved, in blocks
     */
    public static @NotNull IMachineParticleEffect burning(float sidewaysOffset) {
        return mte -> {
            boolean highPressure = mte instanceof SimpleSteamMachine machine && machine.isHighPressure;
            EnumParticleTypes smoke = highPressure ? EnumParticleTypes.SMOKE_LARGE : EnumParticleTypes.SMOKE_NORMAL;
            VanillaParticleEffects.defaultFrontEffect(mte, sidewaysOffset, smoke, EnumParticleTypes.FLAME);

            if (ConfigHolder.machines.machineSounds && GTValues.RNG.nextDouble() < 0.1) {
                BlockPos pos = mte.getPos();
                mte.getWorld().playSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE,
                        SoundCategory.BLOCKS, 1.0F, 1.0F, false);
            }
        };
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SimpleSteamMachine(metaTileEntityId, definition, isHighPressure);
    }

    @Override
    protected boolean isBrickedCasing() {
        return definition.brickedCasing;
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, definition.inputOverlays.length, this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, this, true);
    }

    @Override
    public int getItemOutputLimit() {
        return definition.itemOutputLimit;
    }

    @Override
    protected @NotNull RecipeMapLayout createScreenLayout(@NotNull DoubleSupplier progress) {
        return definition.createLayout(isHighPressure, progress);
    }

    @Override
    public void update() {
        super.update();
        if (definition.tickingEffect != null && getWorld().isRemote && isActive()) {
            definition.tickingEffect.runEffect(this);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (definition.randomEffect != null && isActive()) {
            definition.randomEffect.runEffect(this);
        }
    }
}

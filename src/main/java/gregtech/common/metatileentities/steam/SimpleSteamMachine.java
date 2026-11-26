package gregtech.common.metatileentities.steam;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.particle.IMachineParticleEffect;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.common.ConfigHolder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

/**
 * A steam single-block machine, bronze or at high pressure steel, whose recipe map, overlay, casing, screen and
 * particle effects come from a {@link SteamMachineDefinition}.
 * <p>
 * GTCEu's own steam machines keep their classes, such as {@link SteamFurnace}, so that code naming them keeps
 * working; each of those classes only holds its definition.
 */
public class SimpleSteamMachine extends SteamMetaTileEntity {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_Y = 25;
    private static final int FIRST_INPUT_X = 53;
    private static final int OUTPUT_X = 107;

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
    public ModularUI createUI(EntityPlayer player) {
        TextureArea slot = GuiTextures.SLOT_STEAM.get(isHighPressure);
        ModularUI.Builder builder = createUITemplate(player);
        for (int i = 0; i < definition.inputOverlays.length; i++) {
            builder.slot(this.importItems, i, FIRST_INPUT_X - i * SLOT_SIZE, SLOT_Y, slot,
                    definition.inputOverlays[i].get(isHighPressure));
        }

        builder.progressBar(workableHandler::getProgressPercent, definition.progressX, definition.progressY,
                definition.progressWidth, definition.progressHeight, definition.progressBar.get(isHighPressure),
                definition.progressMoveType, workableHandler.getRecipeMap());
        if (definition.image != null) {
            builder.image(definition.imageX, definition.imageY, definition.imageWidth, definition.imageHeight,
                    definition.image.get(isHighPressure));
        }

        if (definition.outputOverlay == null) {
            builder.slot(this.exportItems, 0, OUTPUT_X, SLOT_Y, true, false, slot);
        } else {
            builder.slot(this.exportItems, 0, OUTPUT_X, SLOT_Y, true, false, slot,
                    definition.outputOverlay.get(isHighPressure));
        }
        return builder.build(getHolder(), player);
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

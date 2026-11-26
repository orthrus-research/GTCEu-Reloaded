package gregtech.common.metatileentities.steam;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.IMachineParticleEffect;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;

public class SteamAlloySmelter extends SimpleSteamMachine {

    private static final IMachineParticleEffect BURNING = burning(0.0F);

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.ALLOY_SMELTER_RECIPES, Textures.ALLOY_SMELTER_OVERLAY)
            .brickedCasing()
            .inputs(GuiTextures.FURNACE_OVERLAY_STEAM, GuiTextures.FURNACE_OVERLAY_STEAM)
            .progressBar(GuiTextures.PROGRESS_BAR_ARROW_STEAM, MoveType.HORIZONTAL, 79, 26, 20, 16)
            .randomEffect(SteamAlloySmelter::smoke)
            .build();

    public SteamAlloySmelter(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    /** It burns as the furnace does, and half the time puffs more smoke beside the flame. */
    private static void smoke(MetaTileEntity mte) {
        BURNING.runEffect(mte);
        if (GTValues.RNG.nextBoolean()) {
            VanillaParticleEffects.defaultFrontEffect(mte, 0.5F, EnumParticleTypes.SMOKE_NORMAL);
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamAlloySmelter(metaTileEntityId, isHighPressure);
    }
}

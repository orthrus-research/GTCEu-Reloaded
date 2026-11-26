package gregtech.common.metatileentities.steam;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;

public class SteamExtractor extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.EXTRACTOR_RECIPES, Textures.EXTRACTOR_OVERLAY)
            .inputs(GuiTextures.EXTRACTOR_OVERLAY_STEAM)
            .progressBar(GuiTextures.PROGRESS_BAR_EXTRACT_STEAM, MoveType.HORIZONTAL, 79, 25, 20, 18)
            .randomEffect(mte -> VanillaParticleEffects.defaultFrontEffect(mte, EnumParticleTypes.CLOUD))
            .build();

    public SteamExtractor(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamExtractor(metaTileEntityId, isHighPressure);
    }
}

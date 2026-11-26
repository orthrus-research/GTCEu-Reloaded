package gregtech.common.metatileentities.steam;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

public class SteamHammer extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.FORGE_HAMMER_RECIPES, Textures.FORGE_HAMMER_OVERLAY)
            .inputs(GuiTextures.HAMMER_OVERLAY_STEAM)
            .progressBar(GuiTextures.PROGRESS_BAR_HAMMER_STEAM, MoveType.VERTICAL_DOWNWARDS, 79, 25, 20, 18)
            .image(GuiTextures.PROGRESS_BAR_HAMMER_BASE_STEAM, 79, 41, 20, 18)
            .randomEffect(VanillaParticleEffects.RANDOM_SPARKS)
            .build();

    public SteamHammer(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamHammer(metaTileEntityId, isHighPressure);
    }
}

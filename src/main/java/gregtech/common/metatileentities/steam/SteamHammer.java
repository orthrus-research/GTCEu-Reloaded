package gregtech.common.metatileentities.steam;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;

public class SteamHammer extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.FORGE_HAMMER_RECIPES, Textures.FORGE_HAMMER_OVERLAY)
            .inputs(GTGuiTextures.HAMMER_OVERLAY_STEAM)
            .progressBar(GTGuiTextures.PROGRESS_BAR_HAMMER_STEAM, Direction.DOWN, 79, 25, 20, 18)
            .image(GTGuiTextures.PROGRESS_BAR_HAMMER_BASE_STEAM, 79, 41, 20, 18)
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

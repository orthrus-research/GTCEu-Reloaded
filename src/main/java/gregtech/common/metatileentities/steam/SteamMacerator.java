package gregtech.common.metatileentities.steam;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;

public class SteamMacerator extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.MACERATOR_RECIPES, Textures.MACERATOR_OVERLAY)
            .inputs(GTGuiTextures.CRUSHED_ORE_OVERLAY_STEAM)
            .progressBar(GTGuiTextures.PROGRESS_BAR_MACERATE_STEAM, Direction.RIGHT, 79, 26, 21, 18)
            .outputOverlay(GTGuiTextures.DUST_OVERLAY_STEAM)
            // its one output slot takes the main product; the byproducts electric macerators give are dropped
            .itemOutputLimit(1)
            .tickingEffect(VanillaParticleEffects.TOP_SMOKE_SMALL)
            .build();

    public SteamMacerator(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamMacerator(metaTileEntityId, isHighPressure);
    }
}

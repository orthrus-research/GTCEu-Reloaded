package gregtech.common.metatileentities.steam;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;

/** The steam compressor makes no particles. */
public class SteamCompressor extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.COMPRESSOR_RECIPES, Textures.COMPRESSOR_OVERLAY)
            .inputs(GTGuiTextures.COMPRESSOR_OVERLAY_STEAM)
            .progressBar(GTGuiTextures.PROGRESS_BAR_COMPRESS_STEAM, Direction.RIGHT, 78, 25, 20, 18)
            .build();

    public SteamCompressor(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamCompressor(metaTileEntityId, isHighPressure);
    }
}

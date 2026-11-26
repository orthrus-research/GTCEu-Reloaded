package gregtech.common.metatileentities.steam;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

/** The steam compressor makes no particles. */
public class SteamCompressor extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.COMPRESSOR_RECIPES, Textures.COMPRESSOR_OVERLAY)
            .inputs(GuiTextures.COMPRESSOR_OVERLAY_STEAM)
            .progressBar(GuiTextures.PROGRESS_BAR_COMPRESS_STEAM, MoveType.HORIZONTAL, 78, 25, 20, 18)
            .build();

    public SteamCompressor(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamCompressor(metaTileEntityId, isHighPressure);
    }
}

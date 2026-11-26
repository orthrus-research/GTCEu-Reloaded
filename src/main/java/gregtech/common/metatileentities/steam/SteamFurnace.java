package gregtech.common.metatileentities.steam;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;

public class SteamFurnace extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.FURNACE_RECIPES, Textures.FURNACE_OVERLAY)
            .brickedCasing()
            .inputs(GTGuiTextures.FURNACE_OVERLAY_STEAM)
            .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW_STEAM, Direction.RIGHT, 79, 26, 20, 16)
            .randomEffect(burning(0.5F))
            .build();

    public SteamFurnace(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, DEFINITION, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamFurnace(metaTileEntityId, isHighPressure);
    }
}

package gregtech.common.metatileentities.steam;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;

public class SteamMacerator extends SimpleSteamMachine {

    private static final SteamMachineDefinition DEFINITION = SteamMachineDefinition
            .builder(RecipeMaps.MACERATOR_RECIPES, Textures.MACERATOR_OVERLAY)
            .inputs(GuiTextures.CRUSHED_ORE_OVERLAY_STEAM)
            .progressBar(GuiTextures.PROGRESS_BAR_MACERATE_STEAM, MoveType.HORIZONTAL, 79, 26, 21, 18)
            .outputOverlay(GuiTextures.DUST_OVERLAY_STEAM)
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

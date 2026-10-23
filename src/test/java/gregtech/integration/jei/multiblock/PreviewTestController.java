package gregtech.integration.jei.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.util.ResourceLocation;

import java.util.function.Function;

class PreviewTestController extends MultiblockControllerBase {

    private final Function<PreviewTestController, BlockPattern> definition;

    PreviewTestController(ResourceLocation id, Function<PreviewTestController, BlockPattern> definition) {
        super(id);
        this.definition = definition;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
        return new PreviewTestController(metaTileEntityId, definition);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return definition.apply(this);
    }

    @Override
    public boolean supportsPreviewEditing() {
        return true;
    }

    @Override
    public void checkStructurePattern() {
        throw new AssertionError("Preview must not form a controller");
    }

    @Override
    protected void updateFormedValid() {
        throw new AssertionError("Preview must not tick a controller");
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart part) {
        return null;
    }
}

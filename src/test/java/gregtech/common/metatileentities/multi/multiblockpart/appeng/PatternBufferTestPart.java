package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;

import java.util.UUID;

import static gregtech.api.util.GTUtility.gregtechId;

final class PatternBufferTestPart extends MetaTileEntityMEPatternBuffer {

    UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000a6");
    MultiblockControllerBase controller = new StockingTestController();

    PatternBufferTestPart() {
        super(gregtechId("pattern_buffer_test"));
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    @Override
    public MultiblockControllerBase getController() {
        return controller;
    }

    @Override
    public AENetworkProxy getProxy() {
        return null;
    } // No real AE grid in the ownership unit fixture.

    IGridProxyable gridOwner() {
        return getGridProxyOwner();
    }
}

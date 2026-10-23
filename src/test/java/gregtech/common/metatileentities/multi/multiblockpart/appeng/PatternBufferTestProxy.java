package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import java.util.UUID;

import static gregtech.api.util.GTUtility.gregtechId;

final class PatternBufferTestProxy extends MetaTileEntityMEPatternBufferProxy {

    UUID owner;
    MultiblockControllerBase controller = new StockingTestController();
    long tick;

    PatternBufferTestProxy(UUID owner) {
        super(gregtechId("pattern_proxy_test"));
        this.owner = owner;
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
    public long getOffsetTimer() {
        return tick;
    }
}

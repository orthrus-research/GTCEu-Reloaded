package gregtech.api.capability.impl;

import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch;

import net.minecraft.util.EnumFacing;

class DualHatchTestPart extends MetaTileEntityDualHatch {

    long tick = 1;
    int itemTransfers;
    int fluidTransfers;

    DualHatchTestPart(int tier, boolean output) {
        super(GTUtility.gregtechId("dual_test_" + tier + "_" + output), tier, output);
        IdleRecipeTestMachine.attachHolder(this);
    }

    @Override
    public long getOffsetTimer() {
        return tick;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void pushItemsIntoNearbyHandlers(EnumFacing... sides) {
        itemTransfers++;
    }

    @Override
    public void pullItemsFromNearbyHandlers(EnumFacing... sides) {
        itemTransfers++;
    }

    @Override
    public void pushFluidsIntoNearbyHandlers(EnumFacing... sides) {
        fluidTransfers++;
    }

    @Override
    public void pullFluidsFromNearbyHandlers(EnumFacing... sides) {
        fluidTransfers++;
    }

    @Override
    public void markDirty() {}
}

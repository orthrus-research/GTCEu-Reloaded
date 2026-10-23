package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidSlot;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;

import static gregtech.api.util.GTUtility.gregtechId;

final class StockingTestHatch extends MetaTileEntityMEStockingHatch {

    final StockingTestStorage<IAEFluidStack> storage = new StockingTestStorage<>(
            (a, b) -> a.getFluidStack().isFluidEqual(b.getFluidStack()));
    boolean connected = true;
    boolean monitorAvailable = true;
    long tick;
    long offset;
    int syncs;
    int statusChecks;
    MultiblockControllerBase controller;

    StockingTestHatch() {
        super(gregtechId("stocking_test_hatch"));
    }

    ExportOnlyAEFluidSlot[] slots() {
        return aeFluidHandler.getInventory();
    }

    void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick++;
            update();
        }
    }

    @Override
    public World getWorld() {
        return DummyWorld.INSTANCE;
    }

    @Override
    public BlockPos getPos() {
        return BlockPos.ORIGIN;
    }

    @Override
    public long getOffsetTimer() {
        return tick + offset;
    }

    @Override
    public MultiblockControllerBase getController() {
        return controller;
    }

    @Override
    public boolean updateMEStatus() {
        statusChecks++;
        this.meStatusChanged = this.isOnline != connected;
        this.isOnline = connected;
        return isOnline;
    }

    @Override
    protected IMEMonitor<IAEFluidStack> getMonitor() {
        return monitorAvailable ? storage.monitor : null;
    }

    @Override
    protected IActionSource getActionSource() {
        return storage.actionSource;
    }

    @Override
    protected void syncME() {
        syncs++;
        super.syncME();
    }
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;

import static gregtech.api.util.GTUtility.gregtechId;

final class StockingTestBus extends MetaTileEntityMEStockingBus {

    final StockingTestStorage<IAEItemStack> storage = new StockingTestStorage<>(
            (a, b) -> a.createItemStack().isItemEqual(b.createItemStack()));
    boolean connected = true;
    boolean monitorAvailable = true;
    long tick;
    long offset;
    int syncs;
    int statusChecks;
    MultiblockControllerBase controller;

    StockingTestBus() {
        super(gregtechId("stocking_test_bus"));
    }

    ExportOnlyAEItemSlot[] slots() {
        return aeItemHandler.getInventory();
    }

    Object itemAbility() {
        return aeItemHandler;
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
    protected IMEMonitor<IAEItemStack> getMonitor() {
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

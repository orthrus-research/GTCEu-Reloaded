package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverFluidRegulator;
import gregtech.common.covers.CoverPump;
import gregtech.common.metatileentities.electric.MetaTileEntityHull;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;

import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FluidRateControlsTest {

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
    }

    public static CoverableView owner() {
        var holder = new MetaTileEntityHull(GTUtility.gregtechId("rate_controls_test"), 1);
        IdleRecipeTestMachine.attachHolder(holder);
        return holder;
    }

    public static CoverDefinition definition() {
        return new CoverDefinition(GTUtility.gregtechId("rate_controls_test"),
                (definition, holder, side) -> new CoverPump(definition, holder, side, 0, 64000),
                new ItemStack(Items.STICK));
    }

    static class Pump extends CoverPump {

        Pump() {
            super(definition(), owner(), EnumFacing.UP, 0, Integer.MAX_VALUE);
        }

        void adjust(int amount) {
            adjustTransferRate(amount);
        }
    }

    static class Conveyor extends CoverConveyor {

        Conveyor() {
            super(definition(), owner(), EnumFacing.UP, 0, Integer.MAX_VALUE);
        }

        void adjust(int amount) {
            adjustTransferRate(amount);
        }
    }

    @Test
    void unitSwitchingPreservesSubBucketPrecisionAndSavedCanonicalRate() {
        var pump = new Pump();
        pump.setTransferRateMilliBuckets(2001);
        for (var unit : CoverPump.BucketMode.VALUES) {
            pump.setBucketMode(unit);
            assertEquals(2001, pump.getTransferRateMilliBuckets());
            var saved = new NBTTagCompound();
            pump.writeToNBT(saved);
            assertEquals(2001, saved.getInteger("TransferRate"));
            var restored = new Pump();
            restored.readFromNBT(saved);
            assertEquals(2001, restored.getTransferRateMilliBuckets());
            assertEquals(unit, restored.getBucketMode());
        }
        assertEquals("2.001", CoverPump.BucketMode.BUCKET.formatRate(2001));
        assertEquals("0.001", CoverPump.BucketMode.BUCKET.formatRate(1));
    }

    @Test
    void displaySettersConvertExactlyOnceAndAdjustmentsDoNotOverflow() {
        var pump = new Pump();
        pump.setBucketMode(CoverPump.BucketMode.BUCKET);
        pump.setTransferRate(3);
        assertEquals(3000, pump.getTransferRateMilliBuckets());
        pump.adjust(1);
        assertEquals(4000, pump.getTransferRateMilliBuckets());
        pump.adjust(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, pump.getTransferRateMilliBuckets());
        pump.adjust(Integer.MIN_VALUE);
        assertEquals(1, pump.getTransferRateMilliBuckets());
        pump.setTransferRate(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, pump.getTransferRateMilliBuckets());
    }

    @Test
    void rateTextRoundTripsCanonicalIntegersAndClampsBeforeExpandingLargeExponents() {
        for (var unit : CoverPump.BucketMode.VALUES) {
            for (int rate : new int[] { 1, 999, 1000, 2001, Integer.MAX_VALUE })
                assertEquals(rate, unit.parseRate(unit.formatRate(rate), Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, unit.parseRate("1e1000000", Integer.MAX_VALUE));
            assertEquals(1, unit.parseRate("-1e1000000", Integer.MAX_VALUE));
            assertThrows(NumberFormatException.class, () -> unit.parseRate("NaN", Integer.MAX_VALUE));
        }
        assertEquals(1001, CoverPump.BucketMode.BUCKET.parseRate("1.0005", Integer.MAX_VALUE));
        assertEquals(640, CoverPump.BucketMode.BUCKET.parseRate("2", 640));
    }

    @Test
    void regulatorBatchTextRetainsFractionalBucketsAndUsesSafeMultiplication() {
        var pump = new Pump();
        pump.getFluidFilterContainer().setMaxTransferSize(Integer.MAX_VALUE);
        pump.setBucketMode(CoverPump.BucketMode.BUCKET);
        pump.setStringTransferRate("2.001");
        assertEquals(2001, pump.getFluidFilterContainer().getTransferSize());
        assertEquals("2.001", pump.getStringTransferRate());
        pump.setStringTransferRate("2147483647");
        assertEquals(Integer.MAX_VALUE, pump.getFluidFilterContainer().getTransferSize());
    }

    @Test
    void conveyorAdjustmentSaturatesInsteadOfWrapping() {
        var conveyor = new Conveyor();
        conveyor.setTransferRate(Integer.MAX_VALUE);
        conveyor.adjust(512);
        assertEquals(Integer.MAX_VALUE, conveyor.getTransferRate());
        conveyor.adjust(Integer.MIN_VALUE);
        assertEquals(1, conveyor.getTransferRate());
    }

    public static void canonicalPacket(CoverPump pump, int amount) throws Exception {
        var manager = new PanelSyncManager(new ModularSyncManager(false), true);
        assertNotNull(pump.buildUI(null, manager, new UISettings()));
        assertNotNull(manager.findSyncHandler("throughput_units"));
        var receiver = (IntSyncValue) manager.findSyncHandler("throughput_millibuckets");
        var sender = new IntSyncValue(() -> amount);
        sender.updateCacheFromSource(true);
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            receiver.readOnServer(0, buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    void nativeRatePacketsAreIndependentOfUnitsAndServerClamped() throws Exception {
        var pump = new CoverPump(definition(), owner(), EnumFacing.UP, 0, 64000);
        pump.setBucketMode(CoverPump.BucketMode.BUCKET);
        canonicalPacket(pump, 2001);
        assertEquals(2001, pump.getTransferRateMilliBuckets());
        canonicalPacket(pump, Integer.MAX_VALUE);
        assertEquals(64000, pump.getTransferRateMilliBuckets());
        canonicalPacket(pump, Integer.MIN_VALUE);
        assertEquals(1, pump.getTransferRateMilliBuckets());
    }

    @Test
    void regulatorBatchPacketsUseCanonicalAmountsIndependentOfSelectedUnits() throws Exception {
        var regulator = new CoverFluidRegulator(definition(), owner(), EnumFacing.UP, 0, 64000);
        regulator.setTransferMode(gregtech.common.covers.TransferMode.KEEP_EXACT);
        regulator.setBucketMode(CoverPump.BucketMode.BUCKET);
        var manager = new PanelSyncManager(new ModularSyncManager(false), true);
        regulator.buildUI(null, manager, new UISettings());
        var receiver = (IntSyncValue) manager.findSyncHandler("filter_transfer_millibuckets");
        var sender = new IntSyncValue(() -> 2001);
        sender.updateCacheFromSource(true);
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            receiver.readOnServer(0, buffer);
        } finally {
            buffer.release();
        }
        assertEquals(2001, regulator.getFluidFilterContainer().getTransferSize());
        assertEquals("2.001", regulator.getStringTransferRate());
        regulator.setBucketMode(CoverPump.BucketMode.MILLI_BUCKET);
        assertEquals(2001, regulator.getFluidFilterContainer().getTransferSize());
    }

    @Test
    void overflowVoidingUsesTheSameCanonicalAmountBoundary() throws Exception {
        var cover = new gregtech.common.covers.CoverFluidVoidingAdvanced(definition(), owner(), EnumFacing.UP);
        cover.setVoidingMode(gregtech.common.covers.VoidingMode.VOID_OVERFLOW);
        cover.setBucketMode(CoverPump.BucketMode.BUCKET);
        var manager = new PanelSyncManager(new ModularSyncManager(false), true);
        assertNotNull(cover.buildUI(null, manager, new UISettings()));
        var receiver = (IntSyncValue) manager.findSyncHandler("filter_transfer_millibuckets");
        var sender = new IntSyncValue(() -> 2001);
        sender.updateCacheFromSource(true);
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            receiver.readOnServer(0, buffer);
        } finally {
            buffer.release();
        }
        assertEquals(2001, cover.getFluidFilterContainer().getTransferSize());
        assertEquals("2.001", cover.getStringTransferRate());
    }

    @Test
    void regulatorBuildsNativeControlsWithoutDuplicateSyncRegistration() throws Exception {
        var regulator = new CoverFluidRegulator(definition(), owner(), EnumFacing.UP, 0, 64000);
        canonicalPacket(regulator, 2001);
        assertEquals(2001, regulator.getTransferRateMilliBuckets());
    }
}

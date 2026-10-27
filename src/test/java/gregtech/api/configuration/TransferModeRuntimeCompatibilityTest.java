package gregtech.api.configuration;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverFluidRegulator;
import gregtech.common.covers.CoverRoboticArm;
import gregtech.common.covers.TransferMode;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/** Stale owner snapshots are explicit fixtures; no extra global enum constant is introduced into the test JVM. */
public class TransferModeRuntimeCompatibilityTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @AfterEach
    void restoreSnapshot() {
        TransferMode.refreshValues();
    }

    @Test
    void publicSnapshotKeepsItsArrayDescriptorAndRefreshesFromActualRuntimeValues() throws ReflectiveOperationException {
        Field field = TransferMode.class.getField("VALUES");
        assertEquals(TransferMode[].class, field.getType());
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isVolatile(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertEquals(0, TransferMode.TRANSFER_ANY.ordinal());
        assertEquals(1, TransferMode.TRANSFER_EXACT.ordinal());
        assertEquals(2, TransferMode.KEEP_EXACT.ordinal());
        TransferMode[] stale = staleSnapshot();

        TransferMode[] refreshed = TransferMode.refreshValues();

        assertArrayEquals(TransferMode.values(), refreshed);
        assertSame(refreshed, TransferMode.VALUES);
        assertNotSame(stale, refreshed);
        assertEquals(1, stale.length, "Already retained external arrays remain snapshots");
    }

    @Test
    void ordinalReaderUsesRuntimeValuesAndPreservesMalformedOrdinalRejection() {
        for (TransferMode mode : TransferMode.values()) {
            staleSnapshot();
            assertSame(mode, TransferMode.byOrdinal(mode.ordinal()));
            assertArrayEquals(TransferMode.values(), TransferMode.VALUES);
        }
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> TransferMode.byOrdinal(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> TransferMode.byOrdinal(TransferMode.values().length));
    }

    @Test
    void roboticArmNbtInitialSyncAndCustomSyncDecodeDespiteAnObsoleteOwnerSnapshot() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("runtime_mode_arm"),
                (def, holder, side) -> new CoverRoboticArm(def, holder, side, 1, 64), ItemStack.EMPTY);
        for (TransferMode mode : TransferMode.values()) {
            CoverRoboticArm source = (CoverRoboticArm) definition.createCover(fixture.pipe.getCoverableImplementation(),
                    EnumFacing.EAST);
            source.setTransferMode(mode);
            NBTTagCompound saved = new NBTTagCompound();
            source.writeToNBT(saved);
            CoverRoboticArm target = (CoverRoboticArm) definition.createCover(fixture.pipe.getCoverableImplementation(),
                    EnumFacing.EAST);
            staleSnapshot();
            target.readFromNBT(saved);
            assertSame(mode, target.getTransferMode());
            assertArrayEquals(TransferMode.values(), TransferMode.VALUES);
            PacketBuffer initial = new PacketBuffer(Unpooled.buffer());
            PacketBuffer custom = new PacketBuffer(Unpooled.buffer());
            try {
                source.writeInitialSyncData(initial);
                staleSnapshot();
                target.readInitialSyncData(initial);
                assertSame(mode, target.getTransferMode());
                assertArrayEquals(TransferMode.values(), TransferMode.VALUES);
                custom.writeByte(mode.ordinal());
                staleSnapshot();
                target.readCustomData(GregtechDataCodes.UPDATE_TRANSFER_MODE, custom);
                assertSame(mode, target.getTransferMode());
                assertArrayEquals(TransferMode.values(), TransferMode.VALUES);
            } finally {
                initial.release();
                custom.release();
            }
        }
    }

    @Test
    void fluidRegulatorNbtAndInitialSyncDecodeDespiteAnObsoleteOwnerSnapshot() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("runtime_mode_regulator"),
                (def, holder, side) -> new CoverFluidRegulator(def, holder, side, 1, 1280), ItemStack.EMPTY);
        for (TransferMode mode : TransferMode.values()) {
            CoverFluidRegulator source = (CoverFluidRegulator) definition.createCover(
                    fixture.pipe.getCoverableImplementation(), EnumFacing.EAST);
            source.setTransferMode(mode);
            NBTTagCompound saved = new NBTTagCompound();
            source.writeToNBT(saved);
            CoverFluidRegulator target = (CoverFluidRegulator) definition.createCover(
                    fixture.pipe.getCoverableImplementation(), EnumFacing.EAST);
            staleSnapshot();
            target.readFromNBT(saved);
            assertSame(mode, target.getTransferMode());
            assertArrayEquals(TransferMode.values(), TransferMode.VALUES);
            PacketBuffer initial = new PacketBuffer(Unpooled.buffer());
            try {
                source.writeInitialSyncData(initial);
                staleSnapshot();
                target.readInitialSyncData(initial);
                assertSame(mode, target.getTransferMode());
                assertArrayEquals(TransferMode.values(), TransferMode.VALUES);
            } finally {
                initial.release();
            }
        }
    }

    private static TransferMode[] staleSnapshot() {
        TransferMode[] stale = { TransferMode.TRANSFER_ANY };
        TransferMode.VALUES = stale;
        return stale;
    }
}

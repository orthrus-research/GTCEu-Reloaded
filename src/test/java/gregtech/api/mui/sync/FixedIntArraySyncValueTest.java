package gregtech.api.mui.sync;

import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FixedIntArraySyncValueTest {

    @Test
    void detectsInPlaceSourceChangesWithoutSharingTheCache() {
        int[] source = { 1, 2 };
        var value = new FixedIntArraySyncValue(() -> source, null);
        var changes = new AtomicInteger();
        value.setChangeListener(changes::incrementAndGet);
        assertNotSame(source, value.getValue());
        assertFalse(value.updateCacheFromSource(false));
        source[0] = 9;
        assertEquals(1, value.getValue(0));
        assertTrue(value.updateCacheFromSource(false));
        assertArrayEquals(source, value.getValue());
        assertEquals(1, changes.get());
        assertFalse(value.updateCacheFromSource(false));
        assertEquals(int[].class, value.getValueType());
    }

    @Test
    void receivedValuesNotifyTheOwnerAndListenerOnBothSides() throws Exception {
        for (boolean client : new boolean[] { true, false }) {
            int[] initial = { 1, 2 };
            var owner = new AtomicReference<int[]>();
            var changes = new AtomicInteger();
            var value = new FixedIntArraySyncValue(() -> initial, owner::set);
            value.setChangeListener(changes::incrementAndGet);
            var buffer = new PacketBuffer(Unpooled.buffer());
            try {
                buffer.writeVarInt(-7);
                buffer.writeVarInt(Integer.MAX_VALUE);
                if (client) value.readOnClient(0, buffer);
                else value.readOnServer(0, buffer);
                assertArrayEquals(new int[] { -7, Integer.MAX_VALUE }, owner.get());
                assertArrayEquals(owner.get(), value.getValue());
                assertNotSame(owner.get(), value.getValue());
                assertArrayEquals(new int[] { 1, 2 }, initial);
                assertEquals(1, changes.get());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void truncatedPacketDoesNotPublishAPartialArray() {
        var calls = new AtomicInteger();
        var value = new FixedIntArraySyncValue(() -> new int[] { 1, 2 }, ignored -> calls.incrementAndGet());
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeVarInt(9);
            assertThrows(IndexOutOfBoundsException.class, () -> value.readOnClient(0, buffer));
            assertArrayEquals(new int[] { 1, 2 }, value.getValue());
            assertEquals(0, calls.get());
        } finally {
            buffer.release();
        }
    }

    @Test
    void explicitNotificationRefreshesAndSynchronizesWithoutWritingSource() {
        int[] source = { 1 };
        var sends = new AtomicInteger();
        var writes = new AtomicInteger();
        var value = new FixedIntArraySyncValue(() -> source, ignored -> writes.incrementAndGet()) {

            @Override
            protected void sync() {
                assertEquals(8, getValue(0));
                sends.incrementAndGet();
            }
        };
        source[0] = 8;
        value.notifyUpdate();
        assertEquals(1, sends.get());
        assertEquals(0, writes.get());
        assertThrows(IllegalArgumentException.class, () -> value.setValue(new int[2], true, false));
        assertEquals(8, value.getValue(0));
    }
}

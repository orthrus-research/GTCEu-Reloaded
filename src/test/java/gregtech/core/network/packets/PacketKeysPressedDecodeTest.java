package gregtech.core.network.packets;

import gregtech.Bootstrap;

import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PacketKeysPressedDecodeTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    private static int decodedEntries(PacketBuffer buffer) throws Exception {
        PacketKeysPressed packet = new PacketKeysPressed();
        packet.decode(buffer);
        Field field = PacketKeysPressed.class.getDeclaredField("serverKeysUpdated");
        field.setAccessible(true);
        return ((int[]) field.get(packet)).length;
    }

    @Test
    public void oversizedCountIsRejected() throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeVarInt(Integer.MAX_VALUE);
        buffer.writeByte(0);
        assertEquals(0, decodedEntries(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    public void normalPacketDecodes() throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeByte(0);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        assertEquals(1, decodedEntries(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}

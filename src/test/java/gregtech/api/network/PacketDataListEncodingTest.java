package gregtech.api.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PacketDataListEncodingTest {

    @Test
    public void updatesRoundTripInOrder() {
        PacketDataList list = new PacketDataList();
        list.add(7, new byte[] { 1, 2, 3 });
        list.add(300, new byte[0]);
        list.add(7, new byte[] { 9 });

        byte[] encoded = list.dumpToBytes();
        assertTrue(list.isEmpty());

        List<Integer> ids = new ArrayList<>();
        List<byte[]> data = new ArrayList<>();
        PacketDataList.forEachEncoded(encoded, (id, buffer) -> {
            ids.add(id);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            data.add(bytes);
        });

        assertEquals(List.of(7, 300, 7), ids);
        assertArrayEquals(new byte[] { 1, 2, 3 }, data.get(0));
        assertArrayEquals(new byte[0], data.get(1));
        assertArrayEquals(new byte[] { 9 }, data.get(2));
    }
}

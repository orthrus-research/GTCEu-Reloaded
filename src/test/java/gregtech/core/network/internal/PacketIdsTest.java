package gregtech.core.network.internal;

import gregtech.BootstrapExtension;
import gregtech.api.network.IPacket;
import gregtech.core.CoreModule;
import gregtech.core.network.packets.PacketBlockParticle;
import gregtech.core.network.packets.PacketClipboard;
import gregtech.core.network.packets.PacketClipboardNBTUpdate;
import gregtech.core.network.packets.PacketClipboardUIWidgetUpdate;
import gregtech.core.network.packets.PacketFluidVeinList;
import gregtech.core.network.packets.PacketKeysPressed;
import gregtech.core.network.packets.PacketNotifyCapeChange;
import gregtech.core.network.packets.PacketPluginSynced;
import gregtech.core.network.packets.PacketRecoverMTE;
import gregtech.core.network.packets.PacketReloadShaders;
import gregtech.core.network.packets.PacketToolbeltSelectionChange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * GTCEu's packets keep the ids clients and servers know them by: the old GUI's three packets are gone, and their ids
 * stay held rather than passing to the packets after them.
 */
@ExtendWith(BootstrapExtension.class)
class PacketIdsTest {

    /** The packets by id from 4 on, as registered before the old GUI's packets were removed. */
    private static final List<Class<? extends IPacket>> PACKETS = List.of(PacketBlockParticle.class,
            PacketClipboard.class, PacketClipboardUIWidgetUpdate.class, PacketPluginSynced.class,
            PacketRecoverMTE.class, PacketKeysPressed.class, PacketFluidVeinList.class, PacketNotifyCapeChange.class,
            PacketReloadShaders.class, PacketClipboardNBTUpdate.class, PacketToolbeltSelectionChange.Server.class,
            PacketToolbeltSelectionChange.Client.class);

    @Test
    void everyPacketKeepsItsId() throws ReflectiveOperationException {
        PacketHandler handler = new PacketHandler(16);
        CoreModule.registerPackets(retired -> handler.retireId(), handler::registerPacket);

        for (int i = 0; i < PACKETS.size(); i++) {
            Class<? extends IPacket> packet = PACKETS.get(i);
            assertEquals(4 + i, handler.getPacketId(packet), packet.getName());
            assertInstanceOf(packet, handler.createPacket(4 + i), packet.getName());
        }
        assertNull(handler.getPacketClass(4 + PACKETS.size()), "a packet beyond the pinned ones");
    }

    @Test
    void theOldGuiPacketIdsAreHeldAndUnknown() throws ReflectiveOperationException {
        PacketHandler handler = new PacketHandler(16);
        CoreModule.registerPackets(retired -> handler.retireId(), handler::registerPacket);

        for (int id = 1; id <= 3; id++) {
            assertNull(handler.getPacketClass(id), "id " + id);
            assertNull(handler.createPacket(id), "id " + id);
        }
    }

    @Test
    void aRetiredIdIsSkipped() {
        PacketHandler handler = new PacketHandler(4);
        assertEquals(1, handler.retireId());
        handler.registerPacket(PacketClipboard.class);
        assertEquals(2, handler.getPacketId(PacketClipboard.class));
    }
}

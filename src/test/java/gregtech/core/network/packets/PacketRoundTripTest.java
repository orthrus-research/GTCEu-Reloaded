package gregtech.core.network.packets;

import gregtech.BootstrapExtension;
import gregtech.api.network.IPacket;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler.FluidVeinWorldEntry;
import gregtech.api.worldgen.config.BedrockFluidDepositDefinition;
import gregtech.common.gui.widget.prospector.ProspectorMode;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import codechicken.lib.vec.Vector3;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Encodes every GregTech packet, decodes the bytes into a packet made the way the network handler makes one, and
 * compares each decoded field with the one sent. {@link PacketKeysPressed} is left out: its encoder reads the
 * client's key bindings, which a server-side test JVM does not have; {@code PacketKeysPressedDecodeTest} covers its
 * decoder.
 */
@ExtendWith(BootstrapExtension.class)
class PacketRoundTripTest {

    /** Packets that cannot be encoded here, with the reason. */
    private static final Map<Class<?>, String> NOT_ENCODABLE = Map.of(PacketKeysPressed.class,
            "its encoder reads the client's key bindings");

    private static BedrockFluidDepositDefinition oil;
    private static BedrockFluidDepositDefinition gas;

    @BeforeAll
    static void registerFluidVeins() {
        oil = fluidVein("round_trip/oil.json", "water", 30);
        gas = fluidVein("round_trip/gas.json", "lava", 5);
    }

    @AfterAll
    static void removeFluidVeins() {
        BedrockFluidVeinHandler.veinList.remove(oil);
        BedrockFluidVeinHandler.veinList.remove(gas);
    }

    static Stream<Named<Supplier<IPacket>>> packets() {
        return Stream.of(
                packet("block particle", () -> new PacketBlockParticle(new BlockPos(12, -3, 400),
                        new Vector3(1.5, 64.25, -7.75), 12)),
                packet("clipboard", () -> new PacketClipboard("line one\nline two, é and ✓")),
                packet("clipboard NBT update", () -> new PacketClipboardNBTUpdate(-1, new BlockPos(5, 70, -9), 7,
                        buffer(3, 1, 4, 1, 5))),
                packet("clipboard widget update", () -> new PacketClipboardUIWidgetUpdate(1, new BlockPos(-30, 4, 8),
                        2, buffer(9, 2, 6))),
                packet("fluid vein list", () -> {
                    HashMap<FluidVeinWorldEntry, Integer> veins = new HashMap<>();
                    veins.put(new FluidVeinWorldEntry(oil, 120, 99_000), 30);
                    veins.put(new FluidVeinWorldEntry(gas, 15, 4), 5);
                    return new PacketFluidVeinList(veins);
                }),
                packet("cape change", () -> new PacketNotifyCapeChange(new UUID(0x1234_5678L, -42L),
                        new ResourceLocation("gregtech", "textures/capes/round_trip.png"))),
                packet("cape removal", () -> new PacketNotifyCapeChange(new UUID(7L, 8L), null)),
                packet("plugin synced", () -> new PacketPluginSynced(0, new BlockPos(100, 1, 100), 3,
                        buffer(0, 127, 255))),
                packet("recover machine", () -> new PacketRecoverMTE(-1, new BlockPos(-1, 255, 1))),
                packet("reload shaders", PacketReloadShaders::new),
                packet("toolbelt selection to the server", () -> new PacketToolbeltSelectionChange.Server(5)),
                packet("toolbelt selection to the client", () -> new PacketToolbeltSelectionChange.Client(3, 40)),
                packet("UI client action", () -> new PacketUIClientAction(11, 22, buffer(1, 2, 3, 4))),
                packet("UI open", () -> new PacketUIOpen(4, buffer(8, 8), 13,
                        List.of(new PacketUIWidgetUpdate(13, 0, buffer(1)),
                                new PacketUIWidgetUpdate(13, 9, buffer())))),
                packet("UI widget update", () -> new PacketUIWidgetUpdate(2, 300, buffer(250, 251))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packets")
    void decodedFieldsEqualTheEncodedOnes(Supplier<IPacket> packet) throws ReflectiveOperationException {
        IPacket sent = packet.get();
        PacketBuffer wire = new PacketBuffer(Unpooled.buffer());
        sent.encode(wire);
        IPacket received = sent.getClass().getDeclaredConstructor().newInstance();
        received.decode(wire);
        assertEquals(0, wire.readableBytes(), "the decoder must read exactly what the encoder wrote");
        assertFieldsEqual(packet.get(), received, sent.getClass().getSimpleName());
    }

    @Test
    void everyPacketClassIsRoundTripped() throws IOException, URISyntaxException {
        Set<String> covered = packets().map(named -> named.getPayload().get().getClass().getName())
                .collect(Collectors.toCollection(TreeSet::new));
        NOT_ENCODABLE.keySet().forEach(type -> covered.add(type.getName()));
        assertEquals(packetClasses(), covered, "a packet class needs a round trip here, or a reason it has none");
    }

    @Test
    void prospectingResultsSurviveTheirSavedForm() {
        PacketProspecting ores = new PacketProspecting(3, -4, 3, -5, 52, -70, ProspectorMode.ORE);
        ores.addBlock(0, 12, 0, "oreCopper");
        ores.addBlock(0, 30, 0, "oreTin");
        ores.addBlock(15, 5, 7, "oreDiamond");
        assertFieldsEqual(ores, PacketProspecting.readPacketData(ores.writePacketData()), "ore prospecting");

        PacketProspecting fluids = new PacketProspecting(0, 0, 1, 1, 8, 8, ProspectorMode.FLUID);
        fluids.addBlock(0, 1, 0, "oil");
        fluids.addBlock(0, 2, 0, "45");
        assertFieldsEqual(fluids, PacketProspecting.readPacketData(fluids.writePacketData()), "fluid prospecting");
    }

    private static Named<Supplier<IPacket>> packet(String name, Supplier<IPacket> packet) {
        return Named.of(name, packet);
    }

    private static PacketBuffer buffer(int... bytes) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        for (int value : bytes) buffer.writeByte(value);
        return buffer;
    }

    private static BedrockFluidDepositDefinition fluidVein(String name, String fluid, int weight) {
        JsonObject json = new JsonParser().parse("{\"weight\": " + weight + ", \"yield\": {\"min\": 10, \"max\": 90}," +
                " \"depletion\": {\"amount\": 1, \"chance\": 50}, \"fluid\": \"" + fluid + "\"}").getAsJsonObject();
        BedrockFluidDepositDefinition definition = new BedrockFluidDepositDefinition(name);
        assertTrue(definition.initializeFromConfig(json));
        return definition;
    }

    /** The concrete packet classes in this package. */
    private static Set<String> packetClasses() throws IOException, URISyntaxException {
        URL url = PacketRoundTripTest.class.getResource("/gregtech/core/network/packets/PacketClipboard.class");
        assertNotNull(url);
        Path folder = Paths.get(url.toURI()).getParent();
        Set<String> classes = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".class")) continue;
                String name = PacketClipboard.class.getPackageName() + '.' +
                        fileName.substring(0, fileName.length() - ".class".length());
                Class<?> type;
                try {
                    type = Class.forName(name, false, PacketRoundTripTest.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new AssertionError(e);
                }
                if (IPacket.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                    classes.add(type.getName());
                }
            }
        }
        return classes;
    }

    /** Compares field by field: buffers by their bytes, vectors by their coordinates, packets recursively. */
    private static void assertFieldsEqual(Object expected, Object actual, String path) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual, path);
        } else if (expected instanceof PacketBuffer buffer) {
            assertArrayEquals(bytes(buffer), bytes((PacketBuffer) actual), path);
        } else if (expected instanceof Vector3 vector) {
            Vector3 other = (Vector3) actual;
            assertArrayEquals(new double[] { vector.x, vector.y, vector.z },
                    new double[] { other.x, other.y, other.z }, path);
        } else if (expected instanceof List<?> list) {
            List<?> other = (List<?>) actual;
            assertEquals(list.size(), other.size(), path);
            for (int i = 0; i < list.size(); i++) assertFieldsEqual(list.get(i), other.get(i), path + '[' + i + ']');
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), path);
            for (int i = 0; i < Array.getLength(expected); i++) {
                assertFieldsEqual(Array.get(expected, i), Array.get(actual, i), path + '[' + i + ']');
            }
        } else if (expected instanceof Map<?, ?> map && map.keySet().stream()
                .allMatch(FluidVeinWorldEntry.class::isInstance)) {
                    assertEquals(describeVeins(map), describeVeins((Map<?, ?>) actual), path);
                } else
            if (expected.getClass().getPackageName().equals(PacketClipboard.class.getPackageName())) {
                assertEquals(expected.getClass(), actual.getClass(), path);
                for (Class<?> type = expected.getClass(); type != Object.class; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                        field.setAccessible(true);
                        try {
                            assertFieldsEqual(field.get(expected), field.get(actual), path + '.' + field.getName());
                        } catch (IllegalAccessException e) {
                            throw new AssertionError(e);
                        }
                    }
                }
            } else {
                assertEquals(expected, actual, path);
            }
    }

    private static Set<String> describeVeins(Map<?, ?> veins) {
        return veins.entrySet().stream().map(entry -> {
            FluidVeinWorldEntry vein = (FluidVeinWorldEntry) entry.getKey();
            return vein.getDefinition().getDepositName() + " yield " + vein.getFluidYield() + " operations " +
                    vein.getOperationsRemaining() + " weight " + entry.getValue();
        }).collect(Collectors.toCollection(TreeSet::new));
    }

    private static byte[] bytes(PacketBuffer buffer) {
        return ByteBufUtil.getBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
    }
}

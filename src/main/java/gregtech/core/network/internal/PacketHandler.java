package gregtech.core.network.internal;

import gregtech.api.network.IPacket;

import net.minecraft.util.IntIdentityHashBiMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class PacketHandler {

    private static final PacketHandler INSTANCE = new PacketHandler(10);

    private final IntIdentityHashBiMap<Class<? extends IPacket>> packetMap;
    private final Int2ObjectMap<Constructor<? extends IPacket>> constructors = new Int2ObjectOpenHashMap<>();

    PacketHandler(int initialCapacity) {
        packetMap = new IntIdentityHashBiMap<>(initialCapacity);
    }

    protected static PacketHandler getInstance() {
        return INSTANCE;
    }

    private int ID = 1;

    /**
     * @throws IllegalArgumentException if the class has no public no-argument constructor
     */
    public void registerPacket(Class<? extends IPacket> packetClass) {
        Constructor<? extends IPacket> constructor;
        try {
            constructor = packetClass.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Packet " + packetClass.getName() + " has no public no-argument constructor", e);
        }
        int id = ID++;
        packetMap.put(packetClass, id);
        constructors.put(id, constructor);
    }

    /**
     * Holds the next id for a packet GTCEu no longer registers, so that the packets registered after it keep their
     * ids. A packet that arrives with a retired id is unknown: the network handler logs and ignores it.
     *
     * @return the retired id
     */
    public int retireId() {
        return ID++;
    }

    public int getPacketId(Class<? extends IPacket> packetClass) {
        return packetMap.getId(packetClass);
    }

    public Class<? extends IPacket> getPacketClass(int packetId) {
        return packetMap.get(packetId);
    }

    /**
     * @return a new, undecoded packet for the id, or {@code null} if no packet is registered under it
     */
    public @Nullable IPacket createPacket(int packetId) throws InvocationTargetException, InstantiationException,
                                                        IllegalAccessException {
        Constructor<? extends IPacket> constructor = constructors.get(packetId);
        return constructor == null ? null : constructor.newInstance();
    }
}

package gregtech.api.util;

import gregtech.api.GTValues;

import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import org.jetbrains.annotations.NotNull;

public class GTControlledRegistry<K, V> extends RegistryNamespaced<K, V> {

    protected boolean frozen;
    protected final int maxId;

    /**
     * A registry that starts frozen and accepts registrations only between {@link #unfreeze()} and {@link #freeze()}.
     */
    public GTControlledRegistry(int maxId) {
        this(maxId, true);
    }

    /**
     * @param frozen false for a registry that accepts registrations from its creation until {@link #freeze()}
     */
    public GTControlledRegistry(int maxId, boolean frozen) {
        this.maxId = maxId;
        this.frozen = frozen;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        if (frozen) {
            throw new IllegalStateException("Registry is already frozen!");
        }

        if (!checkActiveModContainerIsGregtech("freeze")) {
            return;
        }

        this.frozen = true;
    }

    public void unfreeze() {
        if (!frozen) {
            throw new IllegalStateException("Registry is already unfrozen!");
        }

        if (!checkActiveModContainerIsGregtech("unfreeze")) {
            return;
        }

        this.frozen = false;
    }

    private static boolean checkActiveModContainerIsGregtech(String operation) {
        ModContainer container = Loader.instance().activeModContainer();
        if (container != null && container.getModId().equals(GTValues.MODID)) {
            return true;
        }
        GTLog.logger.error("Ignored an attempt to {} a GregTech registry by {}: only GregTech may do so",
                operation, container == null ? "no active mod" : container.getModId());
        return false;
    }

    public void register(int id, @NotNull K key, @NotNull V value) {
        if (frozen) {
            throw new IllegalStateException(
                    String.format("Tried to register %s (%s) with id %d, but the registry is frozen!", value, key,
                            id));
        }
        if (id < 0 || id >= maxId) {
            throw new IndexOutOfBoundsException("Id is out of range: " + id);
        }

        V objectWithId = getObjectById(id);
        if (objectWithId != null) {
            throw new IllegalArgumentException(
                    String.format("Tried to reassign id %d to %s (%s), but it is already assigned to %s (%s)!",
                            id, value, key, objectWithId, getNameForObject(objectWithId)));
        }
        super.putObject(key, value);
        underlyingIntegerMap.put(value, id);
    }

    @Override
    public void putObject(@NotNull K key, @NotNull V value) {
        throw new UnsupportedOperationException("Use #register(int, String, T)");
    }

    public int getIdByObjectName(K key) {
        V valueWithKey = getObject(key);
        return valueWithKey == null ? 0 : getIDForObject(valueWithKey);
    }
}

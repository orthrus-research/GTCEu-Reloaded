package gregtech.api.unification.material.registry;

import gregtech.api.definition.MaterialReplacement;
import gregtech.api.unification.material.Material;
import gregtech.api.util.GTControlledRegistry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class MaterialRegistry extends GTControlledRegistry<String, Material> {

    public MaterialRegistry() {
        super(Short.MAX_VALUE);
    }

    public abstract void register(Material material);

    /** Replace a logical declaration without removing the previous physical numeric entry. */
    public void registerReplacement(Material material, MaterialReplacement replacement) {
        throw new UnsupportedOperationException("This material registry does not support explicit replacement");
    }

    /** Exact logical identity, independent of short-name aliases and the physical registry storing numeric IDs. */
    public @Nullable Material getQualifiedMaterial(@NotNull String name) {
        for (Material material : this) {
            if (material.getResourceLocation().toString().equals(name)) return material;
        }
        return null;
    }

    /** Current logical declarations, including distinct namespaces; superseded physical entries are omitted. */
    @NotNull
    public abstract Collection<Material> getAllMaterials();

    /**
     * Set the fallback material for this registry.
     * Using {@link #getObjectById(int)} or related will still return {@code null} when an entry cannot be found.
     * This is only for manual fallback usage.
     *
     * @param material the fallback material
     */
    public abstract void setFallbackMaterial(@NotNull Material material);

    /**
     * Using {@link #getObjectById(int)} or related will still return {@code null} when an entry cannot be found.
     * This is only for manual fallback usage.
     *
     * @return the fallback material, used for when another material does not exist
     */
    @NotNull
    public abstract Material getFallbackMaterial();

    /**
     * @return the network ID for this registry
     */
    public abstract int getNetworkId();

    @NotNull
    public abstract String getModid();
}

package gregtech.core.unification.material.internal;

import gregtech.api.definition.MaterialReplacement;
import gregtech.api.definition.MaterialReplacementException;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.core.CoreModule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MaterialRegistryImpl extends MaterialRegistry {

    private static int networkIdCounter;

    private final int networkId = networkIdCounter++;
    private final String modid;

    private boolean isRegistryClosed = false;
    private Material fallbackMaterial = null;
    private final Map<String, Material> qualifiedMaterials = new LinkedHashMap<>();

    protected MaterialRegistryImpl(@NotNull String modid) {
        super();
        this.modid = modid;
    }

    @Override
    public void register(Material material) {
        this.register(material.getId(), material.toString(), material);
    }

    @Override
    public void register(int id, @NotNull String key, @NotNull Material value) {
        if (isRegistryClosed) {
            CoreModule.logger.error(
                    "Materials cannot be registered in the PostMaterialEvent (or after)! Must be added in the MaterialEvent. Skipping material {}...",
                    key);
            return;
        }
        String identity = value.getResourceLocation().toString();
        if (qualifiedMaterials.containsKey(identity))
            throw new IllegalArgumentException("Material identity is already registered: " + identity);
        super.register(id, key, value);
        qualifiedMaterials.put(identity, value);
    }

    @Override
    public @Nullable Material getQualifiedMaterial(@NotNull String name) {
        return qualifiedMaterials.get(name);
    }

    @Override
    public void registerReplacement(Material material, MaterialReplacement replacement) {
        Material expected = replacement.expected();
        String identity = material.getRegistryName();
        if (isRegistryClosed || isFrozen()) {
            throw new MaterialReplacementException("material.phase", replacement.origin(), "registration",
                    "Material replacement requires the open MaterialEvent phase");
        }
        if (!identity.equals(expected.getRegistryName())) {
            throw new MaterialReplacementException("material.identity", replacement.origin(), "material",
                    "Replacement " + identity + " does not match expected " + expected.getRegistryName());
        }
        if (qualifiedMaterials.get(identity) != expected || getObjectById(expected.getId()) != expected) {
            throw new MaterialReplacementException("material.stale", replacement.origin(), "expected",
                    "Expected material is not the current declaration: " + identity + " (" + expected.getId() + ")");
        }
        int id = material.getId();
        if (id < 0 || id >= maxId || getObjectById(id) != null) {
            throw new MaterialReplacementException("material.numeric_id", replacement.origin(), "id",
                    "Replacement requires a new unoccupied numeric ID: " + id);
        }
        super.register(id, material.toString(), material);
        qualifiedMaterials.put(identity, material);
    }

    @NotNull
    @Override
    public Collection<Material> getAllMaterials() {
        return Collections.unmodifiableCollection(qualifiedMaterials.values());
    }

    @Override
    public void setFallbackMaterial(@NotNull Material material) {
        this.fallbackMaterial = material;
    }

    @NotNull
    @Override
    public Material getFallbackMaterial() {
        if (this.fallbackMaterial == null) {
            this.fallbackMaterial = MaterialRegistryManager.getInstance().getDefaultFallback();
        }
        return this.fallbackMaterial;
    }

    @Override
    public int getNetworkId() {
        return this.networkId;
    }

    @NotNull
    @Override
    public String getModid() {
        return this.modid;
    }

    public void closeRegistry() {
        this.isRegistryClosed = true;
    }
}

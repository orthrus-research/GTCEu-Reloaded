package gregtech.api.definition;

import gregtech.api.GTValues;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialFlags;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.MaterialRegistry;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates the complete generated-ore naming plan before Forge registries are mutated. */
public final class OreBlockRegistryPlan {

    private final Map<ResourceLocation, OreBlockIdentity> identities;

    private OreBlockRegistryPlan(Map<ResourceLocation, OreBlockIdentity> identities) {
        this.identities = Map.copyOf(identities);
    }

    /** Match the numeric registry iteration used by block creation, including entries hidden by name aliases. */
    public static DefinitionResult<OreBlockRegistryPlan> prepareRegistries(Collection<MaterialRegistry> registries) {
        List<Material> materials = new ArrayList<>();
        for (MaterialRegistry registry : registries) registry.forEach(materials::add);
        return prepare(materials);
    }

    public static DefinitionResult<OreBlockRegistryPlan> prepare(Collection<Material> materials) {
        List<OreBlockIdentity> definitions = new ArrayList<>();
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        for (Material material : materials) {
            if (!material.hasProperty(PropertyKey.ORE) || material.hasFlag(MaterialFlags.DISABLE_ORE_BLOCK)) continue;
            OreBlockIdentity identity = material.getProperty(PropertyKey.ORE).getBlockIdentity();
            if (identity == null) {
                // Existing world IDs are data contracts, independent of the authoring engine.
                identity = new OreBlockIdentity(new SourceOrigin("java", "generated ore blocks", 0, 0,
                        material.getRegistryName(), "blocks"), material.getResourceLocation(),
                        new ResourceLocation(GTValues.MODID, "ore_" + material.getName()));
            }
            if (!identity.material().equals(material.getResourceLocation())) {
                error(errors, identity, "ore.material", "material", "Identity belongs to " + identity.material() +
                        " but was assigned to " + material.getResourceLocation());
            }
            definitions.add(identity);
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);
        return prepareDefinitions(definitions);
    }

    public static DefinitionResult<OreBlockRegistryPlan> prepareDefinitions(List<OreBlockIdentity> definitions) {
        Map<ResourceLocation, OreBlockIdentity> materials = new LinkedHashMap<>();
        Map<ResourceLocation, OreBlockIdentity> names = new LinkedHashMap<>();
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        for (OreBlockIdentity identity : List.copyOf(definitions)) {
            if (materials.putIfAbsent(identity.material(), identity) != null) {
                error(errors, identity, "ore.duplicate_material", "material",
                        "Material is declared more than once: " + identity.material());
            }
            OreBlockIdentity previous = names.putIfAbsent(identity.registryBase(), identity);
            if (previous != null) {
                error(errors, identity, "ore.registry_collision", "registryBase",
                        "Generated ore base " + identity.registryBase() + " is claimed by " + previous.material() +
                                " at " + previous.origin().location() + " and " + identity.material());
            }
        }
        return errors.isEmpty() ? DefinitionResult.success(new OreBlockRegistryPlan(materials)) :
                DefinitionResult.failure(errors);
    }

    public ResourceLocation blockName(ResourceLocation material, int stoneGroup) {
        OreBlockIdentity identity = identities.get(Objects.requireNonNull(material, "material"));
        if (identity == null) throw new IllegalArgumentException("Material has no generated ore identity: " + material);
        return identity.blockName(stoneGroup);
    }

    private static void error(List<DefinitionDiagnostic> errors, OreBlockIdentity identity, String code,
                              String field, String message) {
        errors.add(
                new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, identity.origin(), field, message));
    }
}

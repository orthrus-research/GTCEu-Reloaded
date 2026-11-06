package gregtech.api.definition;

import gregtech.api.GregTechAPI;
import gregtech.api.items.metaitem.MetaOreDictItem;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Startup-only content. A rejected batch changes neither queued definitions nor native items. */
public final class OreItemRegistry {

    private final ResourceLocation carrierId;
    private final Map<Integer, OreItemDefinition> definitions = new LinkedHashMap<>();
    private MetaOreDictItem carrier;

    public OreItemRegistry(ResourceLocation carrierId) {
        this.carrierId = Objects.requireNonNull(carrierId, "carrierId");
    }

    public synchronized DefinitionResult<List<OreItemDefinition>> register(List<OreItemDefinition> additions) {
        additions = List.copyOf(additions);
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        Set<Integer> ids = new HashSet<>(definitions.keySet());
        Set<String> names = new HashSet<>();
        definitions.values().forEach(value -> names.add(value.materialName() + "/" + value.orePrefix()));
        for (OreItemDefinition item : additions) {
            if (carrier != null)
                error(errors, item, "content.frozen", "metadata", "Item registration has already frozen");
            if (item.metadata() < 0 || item.metadata() >= Short.MAX_VALUE) {
                error(errors, item, "content.metadata.range", "metadata", "Metadata must be between 0 and 32766");
            }
            if (!ids.add(item.metadata()))
                error(errors, item, "content.metadata.duplicate", "metadata", "Metadata is already assigned");
            if (!item.materialName().matches("[a-z][a-z0-9_]*")) {
                error(errors, item, "content.name", "materialName", "Use a nonempty lower-case material name");
            }
            if (!names.add(item.materialName() + "/" + item.orePrefix())) {
                error(errors, item, "content.name.duplicate", "materialName",
                        "Item name and prefix are already assigned");
            }
            if (item.rgb() < 0 || item.rgb() > 0xFFFFFF)
                error(errors, item, "content.color", "rgb", "RGB must be a 24-bit color");
            if (item.iconSet().isBlank())
                error(errors, item, "content.icon.empty", "iconSet", "An icon set is required");
            if (item.orePrefix().isBlank())
                error(errors, item, "content.prefix.empty", "orePrefix", "An ore prefix is required");
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);
        additions.forEach(value -> definitions.put(value.metadata(), value));
        return DefinitionResult.success(additions);
    }

    /** Called immediately before Forge item registration. No script engine is involved. */
    public synchronized MetaOreDictItem freeze() {
        if (carrier != null) return carrier;
        var resolved = resolveReferences();
        if (resolved.value().isEmpty()) {
            throw new IllegalStateException(resolved.diagnostics().stream().map(DefinitionDiagnostic::format)
                    .collect(java.util.stream.Collectors.joining("\n")));
        }
        this.carrier = new MetaOreDictItem((short) 0);
        this.carrier.setRegistryName(carrierId);
        for (OreItemDefinition item : definitions.values()) {
            carrier.addOreDictItem(item.metadata(), item.materialName(), item.rgb(),
                    MaterialIconSet.ICON_SETS.get(item.iconSet()), OrePrefix.getPrefix(item.orePrefix()),
                    item.formula());
        }
        return carrier;
    }

    /** Resolve names after material registries exist. Early declarations never initialize OrePrefix/MarkerMaterials. */
    public synchronized DefinitionResult<List<OreItemDefinition>> resolveReferences() {
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        for (OreItemDefinition item : definitions.values()) {
            if (GregTechAPI.markerMaterialRegistry == null) {
                error(errors, item, "content.registry.not_ready", "orePrefix", "Material registries are not ready");
                continue;
            }
            if (!MaterialIconSet.ICON_SETS.containsKey(item.iconSet()))
                error(errors, item, "content.icon.unknown", "iconSet", "Unknown material icon set: " + item.iconSet());
            if (OrePrefix.getPrefix(item.orePrefix()) == null)
                error(errors, item, "content.prefix.unknown", "orePrefix", "Unknown ore prefix: " + item.orePrefix());
        }
        return errors.isEmpty() ? DefinitionResult.success(List.copyOf(definitions.values())) :
                DefinitionResult.failure(errors);
    }

    public synchronized List<OreItemDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public synchronized ItemStack stack(int metadata, int amount) {
        if (carrier == null) throw new IllegalStateException("Content is not registered yet");
        if (!definitions.containsKey(metadata))
            throw new IllegalArgumentException("Unknown custom item metadata: " + metadata);
        if (amount <= 0) throw new IllegalArgumentException("Item amount must be positive");
        return new ItemStack(carrier, amount, metadata);
    }

    private static void error(List<DefinitionDiagnostic> errors, OreItemDefinition item, String code,
                              String field, String message) {
        errors.add(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, item.origin(), field, message));
    }
}

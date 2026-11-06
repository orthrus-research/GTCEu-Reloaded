package gregtech.api.definition;

import java.util.Objects;

/**
 * Startup content with an explicit saved metadata identity. Names refer to native icon-set and ore-prefix registries.
 */
public record OreItemDefinition(SourceOrigin origin, int metadata, String materialName, int rgb,
                                String iconSet, String orePrefix, String formula) {

    public OreItemDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(materialName, "materialName");
        Objects.requireNonNull(iconSet, "iconSet");
        Objects.requireNonNull(orePrefix, "orePrefix");
        formula = formula == null ? "" : formula;
    }
}

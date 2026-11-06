package gregtech.api.recipes.definition;

import gregtech.api.unification.material.Material;

import java.util.Objects;

/** An immutable material quantity in native material units. */
public record RecyclingMaterial(Material material, long amount) {

    public RecyclingMaterial {
        Objects.requireNonNull(material, "material");
    }
}

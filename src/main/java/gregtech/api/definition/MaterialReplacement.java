package gregtech.api.definition;

import gregtech.api.unification.material.Material;

import java.util.Objects;

/** Selects a new logical material declaration while retaining the expected material's physical ID. */
public record MaterialReplacement(SourceOrigin origin, Material expected) {

    public MaterialReplacement {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(expected, "expected");
    }
}

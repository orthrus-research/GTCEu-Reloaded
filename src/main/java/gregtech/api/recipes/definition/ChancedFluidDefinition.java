package gregtech.api.recipes.definition;

import java.util.Objects;

/** Chance and tier boost use the native 1-in-10,000 scale. */
public record ChancedFluidDefinition(FluidValue output, int chance, int tierBoost) {

    public ChancedFluidDefinition {
        Objects.requireNonNull(output, "output");
    }
}

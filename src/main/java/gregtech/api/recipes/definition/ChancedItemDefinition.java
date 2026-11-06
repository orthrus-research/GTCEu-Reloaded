package gregtech.api.recipes.definition;

import java.util.Objects;

/** Chance and tier boost use the native 1-in-10,000 scale. */
public record ChancedItemDefinition(ItemValue output, int chance, int tierBoost) {

    public ChancedItemDefinition {
        Objects.requireNonNull(output, "output");
    }
}

package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.unification.ore.OrePrefix;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Native recovery policy for one item whose per-unit composition is owned by the shared graph. */
public record RecyclingRecoveryDefinition(SourceOrigin origin, ItemValue input,
                                          boolean ignoreArcSmelting, @Nullable OrePrefix prefix)
        implements RecoveryDefinition {

    public RecyclingRecoveryDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(input, "input");
        if (input.isEmpty() || input.stack().getCount() != 1)
            throw new IllegalArgumentException("Recovery generation requires exactly one nonempty input item");
    }
}

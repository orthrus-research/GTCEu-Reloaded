package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.unification.ore.OrePrefix;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Recovery composition per input batch. Component order retains the native extractor selection policy. */
public record RecyclingRecipeDefinition(SourceOrigin origin, ItemValue input, List<RecyclingMaterial> materials,
                                        boolean ignoreArcSmelting, @Nullable OrePrefix prefix)
        implements RecoveryDefinition {

    public RecyclingRecipeDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(input, "input");
        materials = List.copyOf(materials);
    }
}

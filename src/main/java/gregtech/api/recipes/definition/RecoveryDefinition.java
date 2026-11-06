package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

/** Recovery can use an owned composition or explicitly supplied material quantities without claiming metadata. */
public sealed interface RecoveryDefinition permits RecyclingRecoveryDefinition, RecyclingRecipeDefinition {

    SourceOrigin origin();

    ItemValue input();
}

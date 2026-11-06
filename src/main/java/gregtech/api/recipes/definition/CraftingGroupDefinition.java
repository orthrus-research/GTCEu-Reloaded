package gregtech.api.recipes.definition;

import gregtech.api.recipes.RecyclingContribution;

import java.util.List;

/**
 * Immutable authoring state of one accepted crafting group. Native restoration baselines and implicit disables
 * are owned by the registrar, not by this value. It contains no compiled recipes or frontend lifecycle state.
 */
public record CraftingGroupDefinition(List<CraftingMutation> declarations,
                                      List<RecyclingContribution> contributions,
                                      List<RecoveryDefinition> recovery) {

    public CraftingGroupDefinition {
        declarations = List.copyOf(declarations);
        contributions = List.copyOf(contributions);
        recovery = List.copyOf(recovery);
    }
}

package gregtech.api.recipes;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.definition.RecoveryDefinition;

import java.util.List;

/** Adapts typed recipe effects to the native manager's shared ownership graph. */
final class RecipeRecyclingOwnership {

    private final RecyclingOwner owner = new RecyclingOwner();

    List<NativeRecipeRegistration> generatedRecovery(String group) {
        return GregTechAPI.RECYCLING_MANAGER.ownership().generatedRecovery(owner, group);
    }

    Change prepare(String group, List<OwnedRecipeDefinition> recipes, boolean clear,
                   List<OwnedRecipeDefinition> removed, List<OwnedRecipeDefinition> added) {
        var incoming = recipes.stream().filter(OwnedRecipeDefinition::recycling)
                .map(recipe -> RecyclingContribution.of(recipe.origin(),
                        recipe.recipe().getOutputs().getFirst(), recipe.recipe().getInputs()))
                .toList();
        return new Change(GregTechAPI.RECYCLING_MANAGER.ownership()
                .prepareWithRecipes(owner, group, incoming, clear, removed, added));
    }

    Change prepareRecovery(String group, List<RecyclingContribution> contributions,
                           List<? extends RecoveryDefinition> definitions, List<NativeRecipeRegistration> baselines) {
        return new Change(GregTechAPI.RECYCLING_MANAGER.ownership()
                .prepareRecovery(owner, group, contributions, definitions, baselines, false));
    }

    final class Change {

        private final RecyclingOwnershipRegistry.Change change;

        private Change(RecyclingOwnershipRegistry.Change change) {
            this.change = change;
        }

        boolean isCurrent() {
            return change.isCurrent();
        }

        int recoveryRecipes(String group) {
            return change.recoveryRecipes(owner, group);
        }

        int previousRecoveryRecipes() {
            return change.previousRecoveryRecipes(owner);
        }

        void publish() {
            change.publish();
        }
    }
}

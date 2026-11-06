package gregtech.api.recipes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One detached native index update, shared by direct definitions and composition-derived generation. */
final class RecipeRegistrationBatch {

    private RecipeRegistrationBatch() {}

    static List<PreparedRecipeMapChange> prepare(List<OwnedRecipeDefinition> previous,
                                                 List<OwnedRecipeDefinition> prepared) {
        Set<RecipeMap<?>> affected = new LinkedHashSet<>();
        previous.forEach(value -> affected.add(value.map()));
        prepared.forEach(value -> affected.add(value.map()));
        List<PreparedRecipeMapChange> changes = new ArrayList<>();
        for (RecipeMap<?> map : affected) {
            var removed = previous.stream().filter(value -> value.map() == map).map(OwnedRecipeDefinition::recipe)
                    .toList();
            var added = prepared.stream().filter(value -> value.map() == map).map(OwnedRecipeDefinition::recipe)
                    .toList();
            var origin = prepared.stream().filter(value -> value.map() == map).findFirst()
                    .or(() -> previous.stream().filter(value -> value.map() == map).findFirst()).orElseThrow().origin();
            PreparedRecipeMapChange change;
            try {
                change = map.prepareOwnedChange(removed, added);
            } catch (IllegalStateException changed) {
                throw new RecipeRegistrationException("recipe.ownership.changed", origin, changed.getMessage());
            } catch (RuntimeException failed) {
                throw new RecipeRegistrationException("recipe.state.prepare", origin,
                        "Registration state preparation failed: " + failed);
            }
            if (change == null) throw new RecipeRegistrationException("recipe.index.conflict", origin,
                    "Recipe group conflicts with existing inputs in map " + map.unlocalizedName);
            changes.add(change);
        }
        if (!isCurrent(changes)) {
            var origin = !prepared.isEmpty() ? prepared.getFirst().origin() : previous.getFirst().origin();
            throw new RecipeRegistrationException("recipe.registry.changed", origin,
                    "A recipe map changed during preparation");
        }
        return List.copyOf(changes);
    }

    static boolean isCurrent(List<PreparedRecipeMapChange> changes) {
        return changes.stream().allMatch(change -> change.map().getRecipeRevision() == change.revision() &&
                change.states().stream().allMatch(PreparedRecipeRegistrationState::isCurrent));
    }

    static void publish(List<PreparedRecipeMapChange> changes) {
        for (var change : changes) change.map().publishOwnedChange(change);
    }
}

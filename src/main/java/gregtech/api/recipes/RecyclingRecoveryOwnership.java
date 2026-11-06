package gregtech.api.recipes;

import gregtech.api.recipes.definition.RecoveryDefinition;
import gregtech.api.recipes.definition.RecyclingMaterial;
import gregtech.api.recipes.definition.RecyclingRecipeDefinition;
import gregtech.api.recipes.definition.RecyclingRecoveryDefinition;
import gregtech.api.unification.stack.ItemAndMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generated recovery participates in every shared composition transaction, including other owners' changes. */
final class RecyclingRecoveryOwnership {

    private Map<RecyclingGroup, List<RecoveryDefinition>> definitions = new LinkedHashMap<>();
    private Map<RecyclingGroup, List<OwnedRecipeDefinition>> baselines = new LinkedHashMap<>();
    private Map<RecyclingGroup, List<OwnedRecipeDefinition>> generated = Map.of();
    private Map<RecyclingGroup, Map<ItemAndMetadata, PreparedRecovery>> prepared = Map.of();
    private Map<RecyclingGroup, Integer> counts = Map.of();

    List<NativeRecipeRegistration> generated(RecyclingGroup group) {
        return generated.getOrDefault(group, List.of()).stream()
                .map(recipe -> new NativeRecipeRegistration(recipe.map(), recipe.recipe())).toList();
    }

    Change prepare(RecyclingOwnershipRegistry.Change composition, RecyclingGroup addressed, boolean removed,
                   boolean clear, List<RecoveryDefinition> requested, List<NativeRecipeRegistration> adopted,
                   List<OwnedRecipeDefinition> directRemoved, List<OwnedRecipeDefinition> directAdded) {
        var nextDefinitions = new LinkedHashMap<>(definitions);
        var nextBaselines = new LinkedHashMap<>(baselines);
        var previousGenerated = generated.values().stream().flatMap(List::stream).toList();
        Map<RecipeMap<?>, Long> observedRevisions = new IdentityHashMap<>();
        Map<RecipeMap<?>, Set<Recipe>> present = new IdentityHashMap<>();
        for (var recipe : previousGenerated) observe(recipe.map(), present, observedRevisions);
        for (var recipe : baselines.values().stream().flatMap(List::stream).toList())
            observe(recipe.map(), present, observedRevisions);
        for (var recipe : previousGenerated) {
            if (!present.get(recipe.map()).contains(recipe.recipe()))
                throw new RecipeRegistrationException("recipe.ownership.changed", recipe.origin(),
                        "An owned recovery recipe was removed outside its registration group");
        }
        List<OwnedRecipeDefinition> toRemove = new ArrayList<>(previousGenerated);
        List<OwnedRecipeDefinition> toAdd = new ArrayList<>();
        for (var baseline : baselines.values().stream().flatMap(List::stream).toList()) {
            if (present.get(baseline.map()).contains(baseline.recipe()))
                throw new RecipeRegistrationException("recipe.baseline.changed", baseline.origin(),
                        "A displaced recovery baseline was registered outside its owning group");
        }
        if (clear) {
            nextDefinitions.keySet().removeIf(key -> key.owner() == addressed.owner());
            nextBaselines.entrySet().removeIf(entry -> {
                if (entry.getKey().owner() != addressed.owner()) return false;
                toAdd.addAll(entry.getValue());
                return true;
            });
        } else if (requested != null) {
            if (requested.isEmpty()) nextDefinitions.remove(addressed);
            else nextDefinitions.put(addressed, requested);
        } else if (removed) nextDefinitions.remove(addressed);
        if (!adopted.isEmpty()) {
            var source = requested.isEmpty() ?
                    new gregtech.api.definition.SourceOrigin("java", "recovery", 0, 0, addressed.name(), "recipes") :
                    requested.getFirst().origin();
            List<OwnedRecipeDefinition> displaced = new ArrayList<>();
            for (var receipt : adopted) {
                if (receipt.map() != RecipeMaps.MACERATOR_RECIPES && receipt.map() != RecipeMaps.EXTRACTOR_RECIPES &&
                        receipt.map() != RecipeMaps.ARC_FURNACE_RECIPES)
                    throw new RecipeRegistrationException("recipe.baseline.identity", source,
                            "Recovery baseline must belong to a native recovery map");
                if (displaced.stream()
                        .anyMatch(value -> value.map() == receipt.map() && value.recipe() == receipt.recipe()) ||
                        baselines.values().stream().flatMap(List::stream)
                                .anyMatch(
                                        value -> value.map() == receipt.map() && value.recipe() == receipt.recipe()) ||
                        previousGenerated.stream()
                                .anyMatch(value -> value.map() == receipt.map() && value.recipe() == receipt.recipe()))
                    throw new RecipeRegistrationException("recipe.baseline.identity", source,
                            "Recovery baseline is duplicated or already managed by the shared graph");
                displaced.add(new OwnedRecipeDefinition(receipt.map(), receipt.recipe(), source, false, List.of()));
            }
            var retained = new ArrayList<>(nextBaselines.getOrDefault(addressed, List.of()));
            retained.addAll(displaced);
            nextBaselines.put(addressed, List.copyOf(retained));
            toRemove.addAll(displaced);
        }
        Map<RecyclingGroup, List<OwnedRecipeDefinition>> nextGenerated = new LinkedHashMap<>();
        Map<RecyclingGroup, Map<ItemAndMetadata, PreparedRecovery>> nextPrepared = new LinkedHashMap<>();
        Set<Recipe> retainedRecipes = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<RecyclingGroup, Integer> nextCounts = new LinkedHashMap<>();
        for (var entry : nextDefinitions.entrySet()) {
            List<OwnedRecipeDefinition> groupRecipes = new ArrayList<>();
            Map<ItemAndMetadata, PreparedRecovery> groupPrepared = new LinkedHashMap<>();
            var inputs = new HashSet<ItemAndMetadata>();
            for (var definition : entry.getValue()) {
                var key = new ItemAndMetadata(definition.input().stack());
                if (!inputs.add(key))
                    throw new RecipeRegistrationException("recipe.recovery.owner", definition.origin(),
                            "Recovery inputs must be unique within their group");
                RecyclingRecipeDefinition compiled = switch (definition) {
                    case RecyclingRecipeDefinition fixed -> fixed;
                    case RecyclingRecoveryDefinition derived -> {
                        if (!composition.owns(entry.getKey(), key))
                            throw new RecipeRegistrationException("recipe.recovery.owner", definition.origin(),
                                    "Composition-derived recovery needs a contribution owned by its group");
                        var data = composition.resolved().get(key);
                        var materials = data == null ? List.<RecyclingMaterial>of() : data.getMaterials().stream()
                                .filter(value -> value.amount > 0)
                                .map(value -> new RecyclingMaterial(value.material, value.amount)).toList();
                        yield new RecyclingRecipeDefinition(derived.origin(), derived.input(), materials,
                                derived.ignoreArcSmelting(), derived.prefix());
                    }
                };
                var previous = prepared.getOrDefault(entry.getKey(), Map.of()).get(key);
                if (previous != null && previous.declaration() == definition && previous.effective().equals(compiled)) {
                    groupRecipes.addAll(previous.recipes());
                    previous.recipes().forEach(recipe -> retainedRecipes.add(recipe.recipe()));
                    groupPrepared.put(key, previous);
                    nextCounts.merge(entry.getKey(), previous.recipes().size(), Integer::sum);
                    continue;
                }
                var result = RecyclingRecipeCompiler.prepare(compiled);
                if (result.value().isEmpty()) {
                    throw new RecipeRegistrationException(result.diagnostics().getFirst());
                }
                var recipes = result.value().orElseThrow();
                if (recipes.stream().anyMatch(recipe -> recipe.recycling() || !recipe.furnace().isEmpty()))
                    throw new RecipeRegistrationException("recipe.recovery.effects", definition.origin(),
                            "Generated recovery cannot recursively add composition or furnace effects");
                groupRecipes.addAll(recipes);
                groupPrepared.put(key, new PreparedRecovery(definition, compiled, recipes));
                nextCounts.merge(entry.getKey(), recipes.size(), Integer::sum);
            }
            nextGenerated.put(entry.getKey(), List.copyOf(groupRecipes));
            nextPrepared.put(entry.getKey(), Map.copyOf(groupPrepared));
        }
        for (var direct : directRemoved) {
            if (previousGenerated.stream()
                    .anyMatch(recipe -> recipe.map() == direct.map() && recipe.recipe() == direct.recipe()))
                throw new RecipeRegistrationException("recipe.recovery.owner", direct.origin(),
                        "Generated recovery must be changed through its composition owner");
        }
        toRemove.addAll(directRemoved);
        toRemove.removeIf(recipe -> retainedRecipes.contains(recipe.recipe()));
        nextGenerated.values().forEach(values -> values.stream()
                .filter(recipe -> !retainedRecipes.contains(recipe.recipe())).forEach(toAdd::add));
        toAdd.addAll(directAdded);
        return new Change(RecipeRegistrationBatch.prepare(toRemove, toAdd), nextDefinitions, nextBaselines,
                Collections.unmodifiableMap(nextGenerated), Map.copyOf(nextCounts), Map.copyOf(nextPrepared),
                Map.copyOf(observedRevisions));
    }

    private static void observe(RecipeMap<?> map, Map<RecipeMap<?>, Set<Recipe>> present,
                                Map<RecipeMap<?>, Long> revisions) {
        if (present.containsKey(map)) return;
        revisions.put(map, map.getRecipeRevision());
        Set<Recipe> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        map.getRecipesByCategory().values().forEach(identities::addAll);
        present.put(map, identities);
    }

    private record PreparedRecovery(RecoveryDefinition declaration, RecyclingRecipeDefinition effective,
                                    List<OwnedRecipeDefinition> recipes) {}

    final class Change {

        private final List<PreparedRecipeMapChange> recipes;
        private final Map<RecyclingGroup, List<RecoveryDefinition>> nextDefinitions;
        private final Map<RecyclingGroup, List<OwnedRecipeDefinition>> nextBaselines;
        private final Map<RecyclingGroup, List<OwnedRecipeDefinition>> nextGenerated;
        private final Map<RecyclingGroup, Integer> previousCounts = counts;
        private final Map<RecyclingGroup, Integer> nextCounts;
        private final Map<RecyclingGroup, Map<ItemAndMetadata, PreparedRecovery>> nextPrepared;
        private final Map<RecipeMap<?>, Long> observedRevisions;

        private Change(List<PreparedRecipeMapChange> recipes,
                       Map<RecyclingGroup, List<RecoveryDefinition>> nextDefinitions,
                       Map<RecyclingGroup, List<OwnedRecipeDefinition>> nextBaselines,
                       Map<RecyclingGroup, List<OwnedRecipeDefinition>> nextGenerated,
                       Map<RecyclingGroup, Integer> nextCounts,
                       Map<RecyclingGroup, Map<ItemAndMetadata, PreparedRecovery>> nextPrepared,
                       Map<RecipeMap<?>, Long> observedRevisions) {
            this.recipes = recipes;
            this.nextDefinitions = nextDefinitions;
            this.nextBaselines = nextBaselines;
            this.nextGenerated = nextGenerated;
            this.nextCounts = nextCounts;
            this.nextPrepared = nextPrepared;
            this.observedRevisions = observedRevisions;
        }

        int recipes(RecyclingGroup group) {
            return nextCounts.getOrDefault(group, 0);
        }

        int previousRecipes(RecyclingOwner owner) {
            return previousCounts.entrySet().stream().filter(entry -> entry.getKey().owner() == owner)
                    .mapToInt(Map.Entry::getValue).sum();
        }

        boolean isCurrent() {
            return RecipeRegistrationBatch.isCurrent(recipes) && observedRevisions.entrySet().stream()
                    .allMatch(entry -> entry.getKey().getRecipeRevision() == entry.getValue());
        }

        void publish() {
            RecipeRegistrationBatch.publish(recipes);
            definitions = nextDefinitions;
            baselines = nextBaselines;
            generated = nextGenerated;
            prepared = nextPrepared;
            counts = nextCounts;
        }
    }
}

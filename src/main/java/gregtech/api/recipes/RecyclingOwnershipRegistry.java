package gregtech.api.recipes;

import gregtech.api.recipes.definition.RecoveryDefinition;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.api.util.GTUtility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns recycling metadata alongside recipe groups, including restoration of preexisting exact entries. */
public final class RecyclingOwnershipRegistry {

    private final RecyclingManager manager;
    private long generation;
    private final RecyclingRecoveryOwnership recovery = new RecyclingRecoveryOwnership();
    private Map<ItemAndMetadata, RecyclingData> resolvedSnapshot;
    private List<RecyclingFallback> resolvedFallbacks = List.of();
    private long resolvedRevision = -1;

    public RecyclingOwnershipRegistry(RecyclingManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    /** Current generated recipe identities for one owner/group, queried on its quiescent registration thread. */
    public List<NativeRecipeRegistration> generatedRecovery(RecyclingOwner owner, String group) {
        return recovery.generated(new RecyclingGroup(Objects.requireNonNull(owner, "owner"),
                Objects.requireNonNull(group, "group")));
    }

    private Map<RecyclingGroup, Map<ItemAndMetadata, RecyclingData>> groups = new LinkedHashMap<>();
    private Map<ItemAndMetadata, RecyclingData> baselines = new HashMap<>();
    private Map<RecyclingGroup, List<RecyclingContribution>> contributions = new LinkedHashMap<>();

    /** Prepare on a quiescent registration thread; clear removes only this owner's groups. */
    public Change prepare(RecyclingOwner owner, String group, List<RecyclingContribution> incoming, boolean clear) {
        return prepareWithRecipes(owner, group, incoming, clear, List.of(), List.of());
    }

    Change prepareWithRecipes(RecyclingOwner owner, String group, List<RecyclingContribution> incoming, boolean clear,
                              List<OwnedRecipeDefinition> removedRecipes, List<OwnedRecipeDefinition> addedRecipes) {
        incoming = List.copyOf(incoming);
        var composition = prepareMetadata(owner, group, incoming, clear);
        return new Change(composition,
                recovery.prepare(composition, new RecyclingGroup(owner, group), incoming.isEmpty(),
                        clear, null, List.of(), removedRecipes, addedRecipes));
    }

    /** Prepare composition and generated recovery as one transaction. Clear restores this owner's baselines. */
    public Change prepareRecovery(RecyclingOwner owner, String group, List<RecyclingContribution> incoming,
                                  List<? extends RecoveryDefinition> definitions,
                                  List<NativeRecipeRegistration> baselines, boolean clear) {
        incoming = List.copyOf(incoming);
        definitions = List.copyOf(definitions);
        baselines = List.copyOf(baselines);
        if (clear && (!incoming.isEmpty() || !definitions.isEmpty() || !baselines.isEmpty()))
            throw new IllegalArgumentException("Clearing recovery cannot also declare replacements");
        var composition = prepareMetadata(owner, group, incoming, clear);
        return new Change(composition,
                recovery.prepare(composition, new RecyclingGroup(owner, group), incoming.isEmpty(),
                        clear, List.copyOf(definitions), baselines, List.of(), List.of()));
    }

    private Change prepareMetadata(RecyclingOwner owner, String group, List<RecyclingContribution> incoming,
                                   boolean clear) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(group, "group");
        if (group.isBlank()) throw new IllegalArgumentException("Recycling group needs a stable name");
        incoming = List.copyOf(incoming);
        RecyclingGroup addressed = new RecyclingGroup(owner, group);
        long revision = manager.revision();
        long originalGeneration = generation;
        boolean unchanged = incoming.isEmpty() &&
                (clear ? contributions.keySet().stream().noneMatch(key -> key.owner() == owner) :
                        !contributions.containsKey(addressed));
        if (unchanged && resolvedSnapshot != null && revision == resolvedRevision &&
                resolvedFallbacks.stream().allMatch(RecyclingFallback::isCurrent)) {
            return new Change(null, groups, baselines, contributions, null, resolvedSnapshot,
                    originalGeneration, revision, resolvedFallbacks);
        }
        if (contributions.isEmpty() && incoming.isEmpty()) {
            return new Change(null, groups, baselines, contributions,
                    null, manager.snapshot(),
                    originalGeneration, revision, List.of());
        }
        Map<ItemAndMetadata, RecyclingData> original = manager.snapshot();
        Map<ItemAndMetadata, RecyclingData> working = new HashMap<>(original);
        Map<RecyclingGroup, Map<ItemAndMetadata, RecyclingData>> nextGroups = new LinkedHashMap<>();
        Map<ItemAndMetadata, RecyclingData> nextBaselines = new HashMap<>();
        Map<RecyclingGroup, List<RecyclingContribution>> nextContributions = new LinkedHashMap<>(contributions);
        if (clear) nextContributions.keySet().removeIf(key -> key.owner() == owner);
        else if (incoming.isEmpty()) nextContributions.remove(addressed);
        else nextContributions.put(addressed, incoming);
        Map<ItemAndMetadata, RecyclingData> removed = new HashMap<>();
        // Restore the whole owned view before resolving it again. Otherwise retained groups can read stale
        // compositions from an upstream group that was replaced or removed during this transaction.
        groups.values().forEach(removed::putAll);
        removed.forEach((key, data) -> {
            if (!Objects.equals(original.get(key), data)) {
                throw new IllegalStateException("Owned recycling metadata changed outside its group: " + key);
            }
            RecyclingData baseline = baselines.get(key);
            if (baseline == null) working.remove(key);
            else working.put(key, baseline);
        });

        Map<ItemAndMetadata, List<RecyclingContribution>> pending = new LinkedHashMap<>();
        Map<ItemAndMetadata, RecyclingGroup> owners = new HashMap<>();
        nextContributions.forEach((entryOwner, values) -> values.forEach(recipe -> {
            ItemAndMetadata key = recipe.output();
            RecyclingGroup other = owners.putIfAbsent(key, entryOwner);
            if (other != null && !other.equals(entryOwner)) {
                throw new RecyclingOwnershipException(recipe.origin(),
                        "Recycling output is already owned by another group: " + key);
            }
            pending.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
        }));
        Map<ItemAndMetadata, List<RecyclingRoute>> routes = new LinkedHashMap<>();
        pending.forEach((output, values) -> routes.put(output, RecyclingRoute.prepare(values)));
        Map<ItemAndMetadata, RecyclingData> additions = new LinkedHashMap<>();
        Map<ItemAndMetadata, RecyclingFallback> fallbacks = new LinkedHashMap<>();
        var ready = new ArrayDeque<>(pending.keySet());
        Map<ItemAndMetadata, Set<ItemAndMetadata>> waiting = new HashMap<>();
        while (!ready.isEmpty()) {
            ItemAndMetadata output = ready.removeFirst();
            var values = routes.get(output);
            ItemAndMetadata dependency = values.stream().flatMap(recipe -> recipe.items().keySet().stream())
                    .map(input -> pending.containsKey(input) ? input :
                            !working.containsKey(input) && pending.containsKey(input.toWildcard()) ?
                                    input.toWildcard() : null)
                    .filter(Objects::nonNull).findFirst().orElse(null);
            if (dependency != null) {
                // Wake this output only when its dependency resolves. Reverse chains do not rescan every
                // pending recipe on each pass, and wildcard fallback is reconsidered after exact resolution.
                waiting.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(output);
                continue;
            }
            RecyclingData combined = null;
            for (RecyclingRoute recipe : values) {
                RecyclingData data;
                try {
                    data = recipe.resolve(stack -> {
                        var key = new ItemAndMetadata(stack);
                        var value = GTUtility.getOrWildcardMeta(working, key);
                        if (value == null) fallbacks.computeIfAbsent(key, RecyclingFallback::capture);
                        return value;
                    });
                } catch (ArithmeticException invalidAmount) {
                    throw new RecyclingOwnershipException(recipe.origin(),
                            "Recycling composition exceeds its quantity domain: " + invalidAmount.getMessage());
                }
                if (data != null) {
                    if (combined != null && !combined.equals(data)) {
                        throw new RecyclingOwnershipException(recipe.origin(),
                                "Recipes declare conflicting recycling composition for " + output);
                    }
                    combined = data;
                }
            }
            if (combined != null) {
                nextBaselines.put(output, working.get(output));
                working.put(output, combined);
                additions.put(output, combined);
                nextGroups.computeIfAbsent(owners.get(output), ignored -> new LinkedHashMap<>())
                        .put(output, combined);
            }
            pending.remove(output);
            var dependents = waiting.remove(output);
            if (dependents != null) ready.addAll(dependents);
        }
        if (!pending.isEmpty()) throw new RecyclingOwnershipException(
                routes.get(pending.keySet().iterator().next()).getFirst().origin(),
                "Recycling dependencies form a cycle");
        Map<ItemAndMetadata, RecyclingData> replacements = new HashMap<>();
        removed.keySet().forEach(key -> replacements.put(key, working.get(key)));
        additions.keySet().forEach(key -> replacements.put(key, working.get(key)));
        return new Change(manager.prepare(revision, replacements), nextGroups, nextBaselines, nextContributions,
                null, Map.copyOf(working),
                originalGeneration, revision, List.copyOf(fallbacks.values()));
    }

    public final class Change {

        private final long expectedGeneration;
        private final long expectedRevision;

        private final RecyclingManager.PreparedChange nativeChange;
        private final Map<RecyclingGroup, Map<ItemAndMetadata, RecyclingData>> nextGroups;
        private final Map<ItemAndMetadata, RecyclingData> nextBaselines;
        private final Map<RecyclingGroup, List<RecyclingContribution>> nextContributions;
        private final RecyclingRecoveryOwnership.Change recipes;
        private final Map<ItemAndMetadata, RecyclingData> resolved;
        private final List<RecyclingFallback> fallbacks;
        private boolean published;

        private Change(RecyclingManager.PreparedChange nativeChange,
                       Map<RecyclingGroup, Map<ItemAndMetadata, RecyclingData>> nextGroups,
                       Map<ItemAndMetadata, RecyclingData> nextBaselines,
                       Map<RecyclingGroup, List<RecyclingContribution>> nextContributions,
                       RecyclingRecoveryOwnership.Change recipes, Map<ItemAndMetadata, RecyclingData> resolved,
                       long expectedGeneration, long expectedRevision, List<RecyclingFallback> fallbacks) {
            this.nativeChange = nativeChange;
            this.nextGroups = nextGroups;
            this.nextBaselines = nextBaselines;
            this.nextContributions = nextContributions;
            this.recipes = recipes;
            this.resolved = resolved;
            this.expectedGeneration = expectedGeneration;
            this.expectedRevision = expectedRevision;
            this.fallbacks = fallbacks;
        }

        private Change(Change composition, RecyclingRecoveryOwnership.Change recipes) {
            this(composition.nativeChange, composition.nextGroups, composition.nextBaselines,
                    composition.nextContributions, recipes, composition.resolved,
                    composition.expectedGeneration, composition.expectedRevision, composition.fallbacks);
        }

        boolean owns(RecyclingGroup group, ItemAndMetadata output) {
            return nextContributions.getOrDefault(group, List.of()).stream()
                    .anyMatch(contribution -> contribution.output().equals(output));
        }

        int recoveryRecipes(RecyclingOwner owner, String group) {
            return recipes.recipes(new RecyclingGroup(owner, group));
        }

        int previousRecoveryRecipes(RecyclingOwner owner) {
            return recipes.previousRecipes(owner);
        }

        /** Detached exact metadata view for preparing downstream native registrations before publication. */
        public Map<ItemAndMetadata, RecyclingData> resolved() {
            return resolved;
        }

        public boolean isCurrent() {
            return !published && generation == expectedGeneration && manager.revision() == expectedRevision &&
                    (nativeChange == null || nativeChange.isCurrent()) && (recipes == null || recipes.isCurrent()) &&
                    fallbacks.stream().allMatch(RecyclingFallback::isCurrent);
        }

        public void publish() {
            if (!isCurrent()) throw new IllegalStateException("Recycling ownership changed before publication");
            if (nativeChange != null) nativeChange.publish();
            if (recipes != null) recipes.publish();
            groups = nextGroups;
            baselines = nextBaselines;
            contributions = nextContributions;
            resolvedSnapshot = resolved;
            resolvedFallbacks = fallbacks;
            resolvedRevision = manager.revision();
            generation++;
            published = true;
        }
    }
}

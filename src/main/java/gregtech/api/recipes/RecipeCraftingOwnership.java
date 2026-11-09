package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared name claims and per-owner baselines; accessed only during quiescent registration.
 * <p>
 * Claims are released only by the owner's {@code clear()}. A registrar dropped without clearing leaves its recipes
 * published, and its claim keeps another owner from overwriting recipes whose baseline only the dropped owner
 * holds. Releasing claims on garbage collection would make ownership nondeterministic. Owners are long-lived
 * (one per frontend, for example Groovy's registrar) and must call {@code clear()} before they are discarded.
 */
final class RecipeCraftingOwnership {

    private static Map<ResourceLocation, CraftingClaim> claims = new LinkedHashMap<>();
    private static long generation;

    private Map<String, OwnedCraftingGroup> groups = new LinkedHashMap<>();

    Change prepare(String group, Map<ResourceLocation, IRecipe> incoming, SourceOrigin source) {
        var addressed = new CraftingClaim(this, group);
        var previous = groups.get(group);
        var previousBaseline = previous == null ? null : previous.baseline().withFrozenStage();
        Map<ResourceLocation, IRecipe> replacements = new LinkedHashMap<>();
        if (previous != null) {
            previousBaseline.verifyCurrent();
            previousBaseline.names().stream().filter(name -> !incoming.containsKey(name))
                    .filter(name -> !previousBaseline.retains(name, null))
                    .forEach(name -> replacements.put(name, null));
        }
        for (var entry : incoming.entrySet()) {
            var other = claims.get(entry.getKey());
            if (other != null && !other.equals(addressed))
                throw new RecipeRegistrationException("crafting.owner", source,
                        "Crafting name is already owned by another group: " + entry.getKey());
            if (previousBaseline == null || !previousBaseline.retains(entry.getKey(), entry.getValue()))
                replacements.put(entry.getKey(), entry.getValue());
        }
        var transaction = replacements.isEmpty() ? null : CraftingRegistryTransaction.prepare(replacements);
        var nextGroups = new LinkedHashMap<>(groups);
        var nextClaims = new LinkedHashMap<>(claims);
        var baseline = previousBaseline;
        if (transaction != null) {
            var replacement = transaction.preparedRestoration();
            baseline = baseline == null ? replacement : baseline.then(replacement);
        }
        if (baseline != null) {
            if (!baseline.names().isEmpty()) {
                nextGroups.put(group, new OwnedCraftingGroup(baseline, source,
                        (int) incoming.values().stream().filter(recipe -> recipe != null).count()));
                baseline.names().forEach(name -> nextClaims.put(name, addressed));
            }
        }
        return new Change(transaction, nextGroups, nextClaims, previousBaseline);
    }

    Change prepareClear() {
        CraftingRegistryRestoration baseline = null;
        for (var group : groups.values()) {
            try {
                var current = group.baseline().withFrozenStage();
                current.verifyCurrent();
                baseline = baseline == null ? current : baseline.then(current);
            } catch (IllegalStateException conflict) {
                throw new RecipeRegistrationException("crafting.ownership.changed", group.origin(),
                        conflict.getMessage());
            }
        }
        var nextClaims = new LinkedHashMap<>(claims);
        nextClaims.entrySet().removeIf(entry -> entry.getValue().owner() == this);
        return new Change(baseline == null ? null : baseline.prepare(), Map.of(), nextClaims, baseline);
    }

    int recipes() {
        return groups.values().stream().mapToInt(OwnedCraftingGroup::recipes).sum();
    }

    final class Change {

        private final long expectedGeneration = generation;
        private final CraftingRegistryTransaction transaction;
        private final Map<String, OwnedCraftingGroup> nextGroups;
        private final Map<ResourceLocation, CraftingClaim> nextClaims;
        private final CraftingRegistryRestoration observed;
        private boolean published;

        private Change(CraftingRegistryTransaction transaction, Map<String, OwnedCraftingGroup> nextGroups,
                       Map<ResourceLocation, CraftingClaim> nextClaims, CraftingRegistryRestoration observed) {
            this.transaction = transaction;
            this.nextGroups = nextGroups;
            this.nextClaims = nextClaims;
            this.observed = observed;
        }

        boolean isCurrent() {
            return !published && expectedGeneration == generation &&
                    (transaction == null ? observed == null || observed.isCurrent() : transaction.isCurrent());
        }

        void publish() {
            if (!isCurrent()) throw new IllegalStateException("Crafting ownership changed before publication");
            if (transaction != null) transaction.publish();
            published = true;
        }

        void restore() {
            if (!published) throw new IllegalStateException("Crafting participant was not published");
            if (transaction != null) transaction.restore();
            published = false;
        }

        void commitOwnership() {
            if (!published || generation != expectedGeneration)
                throw new IllegalStateException("Crafting ownership changed before commit");
            groups = nextGroups;
            claims = nextClaims;
            generation++;
        }
    }
}

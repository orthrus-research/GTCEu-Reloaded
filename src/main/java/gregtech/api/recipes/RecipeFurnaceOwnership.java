package gregtech.api.recipes;

import gregtech.api.GTValues;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns furnace effects of machine-recipe groups. It never runs while a furnace or machine is ticking. */
final class RecipeFurnaceOwnership {

    private Map<String, List<OwnedFurnaceRecipe>> groups = new LinkedHashMap<>();
    private List<OwnedFurnaceExperience> ownedExperience = List.of();

    Change prepare(String group, List<OwnedRecipeDefinition> recipes, boolean clear) {
        if (groups.isEmpty() && recipes.stream().allMatch(recipe -> recipe.furnace().isEmpty())) {
            return new Change(null, null, null, groups, ownedExperience);
        }
        FurnaceRecipes nativeRecipes = FurnaceRecipes.instance();
        var snapshot = new FurnaceRegistrySnapshot(nativeRecipes.getSmeltingList(), nativeRecipes.experienceList);
        Map<ItemStack, ItemStack> nextRecipes = new IdentityHashMap<>(snapshot.originalRecipes);
        Map<ItemStack, Float> nextExperience = new IdentityHashMap<>(snapshot.originalExperience);
        var previous = groups.values().stream().flatMap(List::stream).toList();
        for (var recipe : previous) {
            try {
                recipe.verify(snapshot.recipes);
                verifyExperience(recipe.output(), recipe.definition().experience(), snapshot.experience, true);
            } catch (RuntimeException conflict) {
                throw new FurnaceOwnershipException(recipe.origin(), conflict);
            }
            nextRecipes.remove(recipe.input());
        }
        for (var value : ownedExperience) {
            try {
                value.verify(snapshot.experience);
            } catch (RuntimeException conflict) {
                var source = previous.stream().filter(recipe -> overlaps(recipe.output(), value.snapshot().stack()))
                        .findFirst();
                throw new FurnaceOwnershipException(source.orElseThrow().origin(), conflict);
            }
            nextExperience.remove(value.key());
        }

        Map<String, List<OwnedFurnaceRecipe>> nextGroups = new LinkedHashMap<>(groups);
        if (clear) nextGroups.clear();
        else nextGroups.remove(group);
        var added = recipes.stream().flatMap(recipe -> recipe.furnace().stream()
                .map(effect -> new OwnedFurnaceRecipe(effect, recipe.origin()))).toList();
        if (!added.isEmpty()) nextGroups.put(group, added);
        var pending = nextGroups.values().stream().flatMap(List::stream).toList();
        // Foreign recipes may have started using our XP entry after registration. Do not change their XP on removal.
        for (ItemStack output : nextRecipes.values()) {
            for (var xp : ownedExperience) {
                if (!overlaps(output, xp.key())) continue;
                float before = effectiveExperience(output, snapshot.experience);
                var replacement = pending.stream().filter(recipe -> overlaps(output, recipe.output())).findFirst();
                float after = replacement.isPresent() ? replacement.orElseThrow().definition().experience() :
                        effectiveExperience(output, nextExperience);
                if (Float.compare(before, after) != 0) {
                    var source = previous.stream().filter(recipe -> overlaps(recipe.output(), xp.key())).findFirst();
                    throw new FurnaceOwnershipException(source.orElseThrow().origin(), new IllegalStateException(
                            "Removing furnace ownership would change an outside recipe's XP"));
                }
            }
        }

        List<OwnedFurnaceExperience> nextOwnedExperience = new ArrayList<>();
        for (var recipe : pending) {
            try {
                if (nextRecipes.keySet().stream().anyMatch(key -> overlaps(key, recipe.input()))) {
                    throw new IllegalStateException("Furnace input conflicts with an existing recipe: " +
                            recipe.input().getItem().getRegistryName() + ':' + recipe.input().getMetadata());
                }
                float desired = recipe.definition().experience();
                // Adding XP to a shared output must not alter unrelated producers.
                for (ItemStack output : nextRecipes.values()) {
                    if (overlaps(output, recipe.output()) &&
                            Float.compare(effectiveExperience(output, nextExperience), desired) != 0) {
                        throw new IllegalStateException(
                                "Furnace experience conflicts with another producer of this output");
                    }
                }
                boolean hasEntry = verifyExperience(recipe.output(), desired, nextExperience, false);
                if (!hasEntry) {
                    var xp = ownedExperience.stream().filter(value -> overlaps(value.key(), recipe.output()) &&
                            Float.compare(value.amount(), desired) == 0).findFirst()
                            .orElseGet(() -> new OwnedFurnaceExperience(recipe.output().copy(), desired));
                    nextExperience.put(xp.key(), desired);
                    nextOwnedExperience.add(xp);
                }
                nextRecipes.put(recipe.input(), recipe.output());
            } catch (RuntimeException conflict) {
                throw new FurnaceOwnershipException(recipe.origin(), conflict);
            }
        }
        return new Change(snapshot, nextRecipes, nextExperience, nextGroups, List.copyOf(nextOwnedExperience));
    }

    static boolean overlaps(ItemStack left, ItemStack right) {
        return left.getItem() == right.getItem() && (left.getMetadata() == right.getMetadata() ||
                left.getMetadata() == GTValues.W || right.getMetadata() == GTValues.W);
    }

    private static boolean verifyExperience(ItemStack output, float desired, Map<ItemStack, Float> table,
                                            boolean requireExisting) {
        float itemOverride = output.getItem().getSmeltingExperience(output);
        if (Float.compare(itemOverride, -1) != 0 && Float.compare(itemOverride, desired) != 0) {
            throw new IllegalStateException("The output item overrides the declared furnace experience");
        }
        boolean found = false;
        for (var entry : table.entrySet()) {
            if (!overlaps(output, entry.getKey())) continue;
            found = true;
            if (Float.compare(entry.getValue(), desired) != 0) {
                throw new IllegalStateException("Conflicting furnace experience entries for the same output");
            }
        }
        if (requireExisting && !found) {
            throw new IllegalStateException("Experience used by an owned furnace recipe was removed externally");
        }
        return found;
    }

    private static float effectiveExperience(ItemStack output, Map<ItemStack, Float> table) {
        float override = output.getItem().getSmeltingExperience(output);
        if (Float.compare(override, -1) != 0) return override;
        Float value = null;
        for (var entry : table.entrySet()) {
            if (!overlaps(output, entry.getKey())) continue;
            if (value != null && Float.compare(value, entry.getValue()) != 0) {
                throw new IllegalStateException("Ambiguous existing furnace experience entries");
            }
            value = entry.getValue();
        }
        return value == null ? 0 : value;
    }

    final class Change {

        private final FurnaceRegistrySnapshot snapshot;
        private final Map<ItemStack, ItemStack> nextRecipes;
        private final Map<ItemStack, Float> nextExperience;
        private final Map<String, List<OwnedFurnaceRecipe>> nextGroups;
        private final List<OwnedFurnaceExperience> nextOwnedExperience;

        private Change(FurnaceRegistrySnapshot snapshot, Map<ItemStack, ItemStack> nextRecipes,
                       Map<ItemStack, Float> nextExperience, Map<String, List<OwnedFurnaceRecipe>> nextGroups,
                       List<OwnedFurnaceExperience> nextOwnedExperience) {
            this.snapshot = snapshot;
            this.nextRecipes = nextRecipes;
            this.nextExperience = nextExperience;
            this.nextGroups = nextGroups;
            this.nextOwnedExperience = nextOwnedExperience;
        }

        void publish() {
            if (snapshot != null) snapshot.publish(nextRecipes, nextExperience);
        }

        void restore() {
            if (snapshot != null) snapshot.restore();
        }

        void commitOwnership() {
            groups = nextGroups;
            ownedExperience = nextOwnedExperience;
        }
    }
}

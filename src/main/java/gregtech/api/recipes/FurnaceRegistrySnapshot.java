package gregtech.api.recipes;

import gregtech.api.recipes.definition.ItemValue;

import net.minecraft.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/** Retains native table identities and mutable stack snapshots for a quiescent registration operation. */
final class FurnaceRegistrySnapshot {

    final Map<ItemStack, ItemStack> recipes;
    final Map<ItemStack, Float> experience;
    final Map<ItemStack, ItemStack> originalRecipes;
    final Map<ItemStack, Float> originalExperience;
    private final Map<ItemStack, ItemValue> recipeKeys = new IdentityHashMap<>();
    private final Map<ItemStack, ItemValue> recipeValues = new IdentityHashMap<>();
    private final Map<ItemStack, ItemValue> experienceKeys = new IdentityHashMap<>();

    FurnaceRegistrySnapshot(Map<ItemStack, ItemStack> recipes, Map<ItemStack, Float> experience) {
        this.recipes = recipes;
        this.experience = experience;
        this.originalRecipes = new IdentityHashMap<>(recipes);
        this.originalExperience = new IdentityHashMap<>(experience);
        recipes.forEach((input, output) -> {
            recipeKeys.put(input, ItemValue.of(input));
            recipeValues.put(input, ItemValue.of(output));
        });
        experience.keySet().forEach(key -> experienceKeys.put(key, ItemValue.of(key)));
    }

    boolean isCurrent() {
        if (recipes.size() != originalRecipes.size() || experience.size() != originalExperience.size()) return false;
        for (var entry : originalRecipes.entrySet()) {
            ItemStack key = entry.getKey();
            if (recipes.get(key) != entry.getValue() || !recipeKeys.get(key).equals(ItemValue.of(key)) ||
                    !recipeValues.get(key).equals(ItemValue.of(entry.getValue())))
                return false;
        }
        for (var entry : originalExperience.entrySet()) {
            if (!entry.getValue().equals(experience.get(entry.getKey())) ||
                    !experienceKeys.get(entry.getKey()).equals(ItemValue.of(entry.getKey())))
                return false;
        }
        return true;
    }

    void publish(Map<ItemStack, ItemStack> nextRecipes, Map<ItemStack, Float> nextExperience) {
        if (!isCurrent()) throw new IllegalStateException("Furnace tables changed during preparation");
        try {
            apply(recipes, nextRecipes);
            apply(experience, nextExperience);
        } catch (RuntimeException failure) {
            try {
                apply(experience, originalExperience);
                apply(recipes, originalRecipes);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                throw new IllegalStateException("Furnace table publication and restoration failed", failure);
            }
            throw new IllegalStateException("Furnace publication failed; original tables restored", failure);
        }
    }

    void restore() {
        apply(experience, originalExperience);
        apply(recipes, originalRecipes);
    }

    /** Keep table objects and unaffected entries intact for consumers retaining their references. */
    private static <V> void apply(Map<ItemStack, V> target, Map<ItemStack, V> next) {
        target.keySet().removeIf(key -> !next.containsKey(key));
        next.forEach((key, value) -> {
            if (target.get(key) != value) target.put(key, value);
        });
    }
}

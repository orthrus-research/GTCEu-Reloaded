package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.properties.impl.ConditionProperty;
import gregtech.api.util.OverlayedItemHandler;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

/** Preflights each append source before its output overlay or accumulated recipe changes. */
public final class RecipeAppendModifier {

    private final Function<Recipe, Recipe> transformation;
    private Recipe firstAdmitted;
    private RecipeModifierResult failure;

    public RecipeAppendModifier(Function<Recipe, Recipe> transformation) {
        this.transformation = Objects.requireNonNull(transformation);
    }

    public @Nullable Recipe adjust(Recipe source) {
        return transformation.apply(source);
    }

    public boolean accepts(Recipe source, ItemStack available) {
        Recipe recipe = adjust(source);
        if (recipe == null) return false;
        if (recipe.getInputs().size() != 1 || recipe.getInputs().get(0).isNonConsumable() ||
                !recipe.getFluidInputs().isEmpty() || !recipe.getFluidOutputs().isEmpty() ||
                !recipe.getChancedFluidOutputs().getChancedEntries().isEmpty()) {
            fail(RecipeModifierResult.Status.UNSUPPORTED, "gregtech.recipe.modifier.append_shape");
            return false;
        }
        return recipe.matches(false, Collections.singletonList(available), Collections.emptyList());
    }

    public int limit(Recipe recipe, RecipeBuilder<?> builder, int requested) {
        try {
            if (firstAdmitted != null && !RecipeModifierCopies.compatibleForAppend(firstAdmitted, recipe)) {
                fail(RecipeModifierResult.Status.UNSUPPORTED, "gregtech.recipe.modifier.append_metadata");
                return 0;
            }
            builder.getRecipeConditions()
                    .and(recipe.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY));
            int limit = RecipeModifierCopies.safeParallelLimit(recipe, requested, true);
            limit = RecipeModifierCopies.safeItemOutputLimit(builder.getAllItemOutputs(), recipe.getAllItemOutputs(),
                    limit);
            limit = Math.min(limit, (Integer.MAX_VALUE - builder.getDuration()) / recipe.getDuration());
            long existingChances = (long) builder.getChancedOutputs().size() + builder.getChancedFluidOutputs().size();
            long newChances = (long) recipe.getChancedOutputs().getChancedEntries().size() +
                    recipe.getChancedFluidOutputs().getChancedEntries().size();
            if (newChances > 0) limit = (int) Math.min(limit,
                    (RecipeModifierCopies.MAX_PREPARED_CHANCE_ENTRIES - existingChances) / newChances);
            if (limit == 0 && requested > 0)
                fail(RecipeModifierResult.Status.INVALID, "gregtech.recipe.modifier.append_bounds");
            return limit;
        } catch (RuntimeException incompatible) {
            fail(RecipeModifierResult.Status.INVALID, "gregtech.recipe.modifier.append_bounds");
            return 0;
        }
    }

    public void admitted(Recipe recipe) {
        if (firstAdmitted == null) this.firstAdmitted = recipe;
    }

    /** Simulate the final builder's normal-output then chance-output order without overflowing grouped integer sums. */
    public int limitOutputSpace(Recipe recipe, RecipeBuilder<?> builder, OverlayedItemHandler outputs, int requested) {
        int low = 0, high = requested;
        while (low < high) {
            int middle = low + (high - low) / 2 + (high - low) % 2;
            outputs.reset();
            boolean fits = insert(outputs, builder.getOutputs(), 1) && insert(outputs, recipe.getOutputs(), middle);
            if (fits) {
                for (var chance : builder.getChancedOutputs()) {
                    if (outputs.insertStackedItemStack(chance.getIngredient(), chance.getIngredient().getCount()) !=
                            0) {
                        fits = false;
                        break;
                    }
                }
            }
            if (fits) {
                for (var chance : recipe.getChancedOutputs().getChancedEntries()) {
                    long amount = (long) chance.getIngredient().getCount() * middle;
                    if (amount > Integer.MAX_VALUE ||
                            outputs.insertStackedItemStack(chance.getIngredient(), (int) amount) != 0) {
                        fits = false;
                        break;
                    }
                }
            }
            if (fits) low = middle;
            else high = middle - 1;
        }
        outputs.reset();
        return low;
    }

    private static boolean insert(OverlayedItemHandler outputs, java.util.List<ItemStack> stacks, int multiplier) {
        for (ItemStack stack : stacks) {
            long amount = (long) stack.getCount() * multiplier;
            if (amount > Integer.MAX_VALUE || outputs.insertStackedItemStack(stack, (int) amount) != 0) return false;
        }
        return true;
    }

    public @Nullable Recipe metadata() {
        return firstAdmitted;
    }

    public @Nullable RecipeModifierResult failure() {
        return failure;
    }

    private void fail(RecipeModifierResult.Status status, String key) {
        if (failure == null) this.failure = RecipeModifierResult.failure(status, "gregtech:append", -1, key);
    }
}

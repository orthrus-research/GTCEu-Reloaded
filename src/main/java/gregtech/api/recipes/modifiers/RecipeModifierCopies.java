package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.chance.output.impl.ChancedFluidOutput;
import gregtech.api.recipes.chance.output.impl.ChancedItemOutput;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.properties.impl.ConditionProperty;
import gregtech.api.util.ItemStackHashStrategy;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owned startup values. Does not change the legacy shallow copy or generic property duplicate contracts. */
public final class RecipeModifierCopies {

    /** Legacy builders expand one chance entry per operation. Keep opted-in preparation allocations bounded. */
    public static final int MAX_PREPARED_CHANCE_ENTRIES = 4096;

    private RecipeModifierCopies() {}

    public static Recipe copy(Recipe source) {
        return apply(source, RecipeModification.IDENTITY);
    }

    /** Same output-limit semantics as the legacy helper, retaining owned inputs, chance logic and source provenance. */
    public static Recipe trim(Recipe source, int itemLimit, int fluidLimit) {
        if (itemLimit < -1 || fluidLimit < -1) throw new IllegalArgumentException("Invalid recipe output limits");
        Recipe owned = copy(source);
        var items = owned.getItemAndChanceOutputs(itemLimit);
        var fluids = owned.getFluidAndChanceOutputs(fluidLimit);
        return owned.copyWithPreservedMetadata(owned.getInputs(), items.getLeft(),
                new ChancedOutputList<>(owned.getChancedOutputs().getChancedOutputLogic(), items.getRight()),
                owned.getFluidInputs(), fluids.getLeft(),
                new ChancedOutputList<>(owned.getChancedFluidOutputs().getChancedOutputLogic(), fluids.getRight()),
                owned.getDuration(), owned.getEUt(), owned.propertyStorage());
    }

    /** Restore the opt-in source metadata after a legacy numeric/parallel builder has constructed new contents. */
    public static Recipe retainMetadata(Recipe contents, Recipe source) {
        Recipe owned = apply(contents, RecipeModification.IDENTITY, true);
        RecipeConditionSet conditions = source.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY)
                .and(owned.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY));
        return source.copyWithPreservedMetadata(owned.getInputs(), owned.getOutputs(),
                new ChancedOutputList<>(source.getChancedOutputs().getChancedOutputLogic(),
                        owned.getChancedOutputs().getChancedEntries()),
                owned.getFluidInputs(), owned.getFluidOutputs(),
                new ChancedOutputList<>(source.getChancedFluidOutputs().getChancedOutputLogic(),
                        owned.getChancedFluidOutputs().getChancedEntries()),
                owned.getDuration(), owned.getEUt(),
                copyProperties(source.propertyStorage(), conditions));
    }

    /** APPEND_ITEMS does not invent a merge policy for arbitrary properties or mixed chance logic. */
    public static boolean compatibleForAppend(Recipe first, Recipe next) {
        if ((!first.getChancedOutputs().getChancedEntries().isEmpty() ||
                !next.getChancedOutputs().getChancedEntries().isEmpty()) &&
                first.getChancedOutputs().getChancedOutputLogic() != next.getChancedOutputs().getChancedOutputLogic())
            return false;
        if ((!first.getChancedFluidOutputs().getChancedEntries().isEmpty() ||
                !next.getChancedFluidOutputs().getChancedEntries().isEmpty()) &&
                first.getChancedFluidOutputs().getChancedOutputLogic() !=
                        next.getChancedFluidOutputs().getChancedOutputLogic())
            return false;
        return propertyPayloads(first).equals(propertyPayloads(next));
    }

    /** Clamp legacy multiplication before its unchecked integer operations can overflow an adjusted channel. */
    public static int safeParallelLimit(Recipe recipe, int requested, boolean multiplyDuration) {
        long limit = requested;
        if (multiplyDuration) limit = Math.min(limit, Integer.MAX_VALUE / (long) recipe.getDuration());
        else if (recipe.getEUt() != 0) limit = Math.min(limit, Long.MAX_VALUE / Math.abs(recipe.getEUt()));
        for (GTRecipeInput input : recipe.getInputs()) {
            if (!input.isNonConsumable()) limit = Math.min(limit, Integer.MAX_VALUE / (long) input.getAmount());
        }
        for (GTRecipeInput input : recipe.getFluidInputs()) {
            if (!input.isNonConsumable()) limit = Math.min(limit, Integer.MAX_VALUE / (long) input.getAmount());
        }
        for (ItemStack stack : recipe.getOutputs())
            limit = Math.min(limit, Integer.MAX_VALUE / (long) stack.getCount());
        for (FluidStack stack : recipe.getFluidOutputs())
            limit = Math.min(limit, Integer.MAX_VALUE / (long) stack.amount);
        long chances = (long) recipe.getChancedOutputs().getChancedEntries().size() +
                recipe.getChancedFluidOutputs().getChancedEntries().size();
        if (chances > 0) limit = Math.min(limit, MAX_PREPARED_CHANCE_ENTRIES / chances);
        return safeItemOutputLimit(Collections.emptyList(), recipe.getAllItemOutputs(), (int) Math.max(0, limit));
    }

    /**
     * Bound aggregate identities before legacy integer-count maps or builders see them, including worst-case chances.
     */
    public static int safeItemOutputLimit(List<ItemStack> existing, List<ItemStack> added, int requested) {
        Object2LongMap<ItemStack> before = itemAmounts(existing), next = itemAmounts(added);
        long limit = requested;
        for (long amount : before.values()) if (amount > Integer.MAX_VALUE) return 0;
        for (Object2LongMap.Entry<ItemStack> entry : next.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount > 0) limit = Math.min(limit,
                    (Integer.MAX_VALUE - before.getLong(entry.getKey())) / amount);
        }
        return (int) Math.max(0, limit);
    }

    private static Object2LongMap<ItemStack> itemAmounts(List<ItemStack> stacks) {
        Object2LongMap<ItemStack> result = new Object2LongLinkedOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) result.put(stack, Math.addExact(result.getLong(stack), stack.getCount()));
        }
        return result;
    }

    private static Map<RecipeProperty<?>, NBTBase> propertyPayloads(Recipe recipe) {
        Map<RecipeProperty<?>, NBTBase> properties = new java.util.LinkedHashMap<>();
        for (var entry : recipe.propertyStorage().entrySet()) {
            if (!entry.getKey().equals(ConditionProperty.getInstance()))
                properties.put(entry.getKey(), Objects.requireNonNull(entry.getKey().serialize(entry.getValue())));
        }
        return properties;
    }

    static Recipe apply(Recipe source, RecipeModification modification) {
        return apply(source, modification, false);
    }

    private static Recipe apply(Recipe source, RecipeModification modification, boolean prepared) {
        if ((long) source.getChancedOutputs().getChancedEntries().size() +
                source.getChancedFluidOutputs().getChancedEntries().size() > MAX_PREPARED_CHANCE_ENTRIES)
            throw new IllegalArgumentException("Prepared chance entry limit exceeded");
        long originalEU = source.getEUt();
        if (originalEU == Long.MIN_VALUE) throw new IllegalArgumentException("Unrepresentable EU magnitude");
        long eut = modification.energy().apply(Math.abs(originalEU), Long.MAX_VALUE, true, false);
        if (originalEU < 0) eut = -eut;
        int duration = (int) modification.duration().apply(source.getDuration(), Integer.MAX_VALUE, true, false);
        List<GTRecipeInput> items = copyInputs(source.getInputs(), modification.itemInputs(), prepared);
        List<GTRecipeInput> fluids = copyInputs(source.getFluidInputs(), modification.fluidInputs(), prepared);
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack stack : source.getOutputs()) {
            ItemStack result = scale(stack, modification.itemOutputs());
            if (!result.isEmpty()) outputs.add(result);
        }
        List<FluidStack> fluidOutputs = new ArrayList<>();
        for (FluidStack stack : source.getFluidOutputs()) {
            FluidStack result = scale(stack, modification.fluidOutputs());
            if (result.amount > 0) fluidOutputs.add(result);
        }
        List<ChancedItemOutput> chances = new ArrayList<>();
        for (ChancedItemOutput entry : source.getChancedOutputs().getChancedEntries()) {
            ItemStack result = scale(entry.getIngredient(), modification.itemOutputs());
            if (!result.isEmpty())
                chances.add(new ChancedItemOutput(result, entry.getChance(), entry.getChanceBoost()));
        }
        List<ChancedFluidOutput> fluidChances = new ArrayList<>();
        for (ChancedFluidOutput entry : source.getChancedFluidOutputs().getChancedEntries()) {
            FluidStack result = scale(entry.getIngredient(), modification.fluidOutputs());
            if (result.amount > 0)
                fluidChances.add(new ChancedFluidOutput(result, entry.getChance(), entry.getChanceBoost()));
        }
        RecipeConditionSet conditions = source.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY)
                .and(modification.conditions());
        return source.copyWithPreservedMetadata(items, outputs,
                new ChancedOutputList<>(source.getChancedOutputs().getChancedOutputLogic(), chances), fluids,
                fluidOutputs,
                new ChancedOutputList<>(source.getChancedFluidOutputs().getChancedOutputLogic(), fluidChances),
                duration, eut, copyProperties(source.propertyStorage(), conditions));
    }

    private static List<GTRecipeInput> copyInputs(List<GTRecipeInput> inputs, RecipeAmountModifier amountModifier,
                                                  boolean prepared) {
        List<GTRecipeInput> result = new ArrayList<>();
        for (GTRecipeInput input : inputs) {
            // A turbine may legitimately spend previously credited excess voltage with zero newly consumed fuel.
            if (prepared && input.getAmount() == 0 && !input.isNonConsumable()) continue;
            int amount = input.isNonConsumable() ? input.getAmount() :
                    (int) amountModifier.apply(input.getAmount(), Integer.MAX_VALUE, true, false);
            GTRecipeInput copy = RecipeModifierIngredientCopies.copy(input, amount);
            if (copy == null || copy.getAmount() != amount || copy.isNonConsumable() != input.isNonConsumable() ||
                    copy.getClass() != input.getClass() || !copy.equalIgnoreAmount(input) ||
                    !input.equalIgnoreAmount(copy) ||
                    (copy == input && amount != input.getAmount()))
                throw new UnsupportedOperationException("Ingredient does not honor copyWithAmount");
            result.add(copy);
        }
        return result;
    }

    private static ItemStack scale(ItemStack original, RecipeAmountModifier modifier) {
        int amount = (int) modifier.apply(original.getCount(), Integer.MAX_VALUE, false, true);
        ItemStack result = original.copy();
        result.setCount(amount);
        return result;
    }

    private static FluidStack scale(FluidStack original, RecipeAmountModifier modifier) {
        FluidStack result = original.copy();
        result.amount = (int) modifier.apply(original.amount, Integer.MAX_VALUE, false, true);
        return result;
    }

    public static RecipePropertyStorage copyProperties(RecipePropertyStorage source, RecipeConditionSet conditions) {
        RecipePropertyStorage result = new RecipePropertyStorageImpl();
        for (Map.Entry<RecipeProperty<?>, Object> entry : source.entrySet()) {
            RecipeProperty<?> property = entry.getKey();
            if (property.equals(ConditionProperty.getInstance())) continue;
            NBTBase encoded = property.serialize(entry.getValue());
            if (encoded == null || !result.store(property, property.deserialize(encoded.copy())))
                throw new IllegalArgumentException("Cannot copy recipe property: " + property.getKey());
        }
        if (!conditions.isEmpty() && !result.store(ConditionProperty.getInstance(), conditions))
            throw new IllegalArgumentException("Cannot retain recipe conditions");
        return result;
    }
}

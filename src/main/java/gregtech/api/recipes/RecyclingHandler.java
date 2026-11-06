package gregtech.api.recipes;

import gregtech.api.GregTechAPI;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.MarkerMaterial;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.api.unification.stack.UnificationEntry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RecyclingHandler {

    public static @Nullable RecyclingData getRecyclingIngredients(int outputCount, @NotNull Object... recipe) {
        return getRecyclingIngredients(shapedInputs(recipe), outputCount,
                GregTechAPI.RECYCLING_MANAGER::getRecyclingData);
    }

    /** Counts consumed crafting cells, skipping tool symbols; input stack size does not multiply a cell. */
    static Map<ItemAndMetadata, Long> shapedInputs(Object... recipe) {
        Char2IntOpenHashMap inputCountMap = new Char2IntOpenHashMap();
        Map<ItemAndMetadata, Long> inputs = new LinkedHashMap<>();

        int itr = 0;
        while (itr < recipe.length && recipe[itr] instanceof String s) {
            for (char c : s.toCharArray()) {
                if (ToolHelper.getToolFromSymbol(c) != null) continue; // skip tools
                int count = inputCountMap.getOrDefault(c, 0);
                inputCountMap.put(c, count + 1);
            }
            itr++;
        }

        char lastChar = ' ';
        for (int i = itr; i < recipe.length; i++) {
            Object ingredient = recipe[i];

            // Track the current working ingredient symbol
            if (ingredient instanceof Character) {
                lastChar = (char) ingredient;
                continue;
            }

            // Should never happen if recipe is formatted correctly
            // In the case that it isn't, this error should be handled
            // by an earlier method call parsing the recipe.
            if (lastChar == ' ') return Map.of();

            ItemStack stack;
            if (ingredient instanceof MetaItem.MetaValueItem) {
                stack = ((MetaItem<?>.MetaValueItem) ingredient).getStackForm();
            } else if (ingredient instanceof UnificationEntry) {
                stack = OreDictUnifier.get((UnificationEntry) ingredient);
            } else if (ingredient instanceof ItemStack) {
                stack = (ItemStack) ingredient;
            } else if (ingredient instanceof Item) {
                stack = new ItemStack((Item) ingredient, 1);
            } else if (ingredient instanceof Block) {
                stack = new ItemStack((Block) ingredient, 1);
            } else if (ingredient instanceof String) {
                stack = OreDictUnifier.get((String) ingredient);
            } else if (ingredient instanceof Ingredient matching) {
                ItemStack[] alternatives = matching.getMatchingStacks();
                stack = alternatives.length == 0 ? ItemStack.EMPTY : alternatives[0];
            } else continue; // throw out bad entries

            long count = inputCountMap.get(lastChar);
            if (!stack.isEmpty() && count > 0) inputs.merge(new ItemAndMetadata(stack), count, Math::addExact);
        }

        return Map.copyOf(inputs);
    }

    public static @Nullable RecyclingData getRecyclingIngredients(List<GTRecipeInput> inputs, int outputCount) {
        return getRecyclingIngredients(inputs, outputCount, GregTechAPI.RECYCLING_MANAGER::getRecyclingData);
    }

    /** Resolves existing material data from an isolated registration view when preparing a transaction. */
    public static @Nullable RecyclingData getRecyclingIngredients(List<GTRecipeInput> inputs, int outputCount,
                                                                  Function<ItemStack, RecyclingData> recycling) {
        requireOutputCount(outputCount);
        Map<Material, BigInteger> materialStacksExploded = new HashMap<>();
        for (GTRecipeInput input : inputs) {
            if (input == null || !input.isRecyclingInput()) continue;
            ItemStack[] alternatives = input.getInputStacks();
            if (alternatives == null || alternatives.length == 0) continue;
            addItemStackToMaterialStacks(alternatives[0], materialStacksExploded, input.getAmount(), recycling);
        }
        return composition(materialStacksExploded, outputCount);
    }

    /** Resolves a detached contribution without retaining builders, ingredients or frontend state. */
    public static @Nullable RecyclingData getRecyclingIngredients(Map<ItemAndMetadata, Long> inputs, int outputCount,
                                                                  Function<ItemStack, RecyclingData> recycling) {
        requireOutputCount(outputCount);
        Map<Material, BigInteger> materialStacksExploded = new HashMap<>();
        inputs.forEach((input, amount) -> addItemStackToMaterialStacks(input.toItemStack(),
                materialStacksExploded, amount, recycling));
        return composition(materialStacksExploded, outputCount);
    }

    static Map<Material, BigInteger> materialTotals(ItemStack input, Function<ItemStack, RecyclingData> recycling) {
        Map<Material, BigInteger> totals = new HashMap<>();
        addItemStackToMaterialStacks(input, totals, 1, recycling);
        return totals;
    }

    private static @Nullable RecyclingData composition(Map<Material, BigInteger> materialStacksExploded,
                                                       int outputCount) {
        if (materialStacksExploded.isEmpty()) return null;
        BigInteger divisor = BigInteger.valueOf(outputCount);
        return new RecyclingData(materialStacksExploded.entrySet().stream()
                .map(e -> new MaterialStack(e.getKey(), e.getValue().divide(divisor).longValueExact()))
                .sorted(Comparator.<MaterialStack>comparingLong(m -> m.amount).reversed()
                        .thenComparing(m -> m.material.getRegistryName().toString()))
                .collect(Collectors.toList()));
    }

    private static void requireOutputCount(int outputCount) {
        if (outputCount <= 0) throw new IllegalArgumentException("Recycling output count must be positive");
    }

    private static void addItemStackToMaterialStacks(@NotNull ItemStack itemStack,
                                                     @NotNull Map<Material, BigInteger> materialStacksExploded,
                                                     int inputCount) {
        addItemStackToMaterialStacks(itemStack, materialStacksExploded, inputCount,
                GregTechAPI.RECYCLING_MANAGER::getRecyclingData);
    }

    private static void addItemStackToMaterialStacks(ItemStack itemStack,
                                                     Map<Material, BigInteger> materialStacksExploded,
                                                     long inputCount, Function<ItemStack, RecyclingData> recycling) {
        if (inputCount < 0) throw new ArithmeticException("Negative recycling input quantity");
        if (inputCount == 0 || itemStack.isEmpty()) return;
        // First try to get Recycling Data
        RecyclingData data = recycling.apply(itemStack);
        if (data != null) {
            for (MaterialStack ms : data.getMaterials()) {
                if (!(ms.material instanceof MarkerMaterial)) {
                    addMaterialStack(materialStacksExploded, inputCount, ms);
                }
            }
            return;
        }

        // Then try to get a single Material (UnificationEntry needs this, for example)
        MaterialStack materialStack = OreDictUnifier.getMaterial(itemStack);
        // Prefixes such as plank/slab use -1 for an unspecified quantity. It is not signed composition data.
        // Explicit RecyclingData still goes through quantity validation above.
        if (materialStack != null && materialStack.amount >= 0 && !(materialStack.material instanceof MarkerMaterial)) {
            addMaterialStack(materialStacksExploded, inputCount, materialStack);
        }

        // Gather any secondary materials if this item has an OrePrefix
        OrePrefix prefix = OreDictUnifier.getPrefix(itemStack);
        if (prefix != null && !prefix.secondaryMaterials.isEmpty()) {
            for (MaterialStack ms : prefix.secondaryMaterials) {
                addMaterialStack(materialStacksExploded, inputCount, ms);
            }
        }
    }

    /**
     * Adds a MaterialStack to a map of {@code <Material, Quantity>}
     *
     * @param materialStacksExploded the map to add to
     * @param inputCount             the number of items in the stack
     * @param ms                     the stack to add
     */
    private static void addMaterialStack(@NotNull Map<Material, BigInteger> materialStacksExploded,
                                         long inputCount, @NotNull MaterialStack ms) {
        if (ms.amount < 0) throw new ArithmeticException("Negative recycling material quantity");
        BigInteger amount = BigInteger.valueOf(ms.amount).multiply(BigInteger.valueOf(inputCount));
        materialStacksExploded.merge(ms.material, amount, BigInteger::add);
    }
}

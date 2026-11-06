package gregtech.api.recipes;

import gregtech.api.recipes.ingredients.GTRecipeInput;

import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Private compiled ingredient; display callers cannot mutate its matching prototypes. */
final class NativeCraftingIngredient extends Ingredient {

    private final GTRecipeInput input;

    NativeCraftingIngredient(GTRecipeInput input) {
        super(Arrays.stream(input.getInputStacks()).map(ItemStack::copy).toArray(ItemStack[]::new));
        this.input = input;
    }

    @Override
    public boolean apply(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && input.acceptsStack(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public ItemStack[] getMatchingStacks() {
        if (input.isOreDict()) return expandedStacks();
        return Arrays.stream(input.getInputStacks()).map(ItemStack::copy).toArray(ItemStack[]::new);
    }

    @Override
    public IntList getValidItemStacksPacked() {
        ItemStack[] stacks = expandedStacks();
        var packed = new IntArrayList(stacks.length);
        for (ItemStack stack : stacks) packed.add(RecipeItemHelper.pack(stack));
        packed.sort(IntComparators.NATURAL_COMPARATOR);
        return packed;
    }

    private ItemStack[] expandedStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : input.getInputStacks()) {
            if (stack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                var variants = NonNullList.<ItemStack>create();
                stack.getItem().getSubItems(CreativeTabs.SEARCH, variants);
                for (ItemStack variant : variants) {
                    ItemStack copy = variant.copy();
                    copy.setCount(stack.getCount());
                    result.add(copy);
                }
            } else result.add(stack.copy());
        }
        return result.toArray(ItemStack[]::new);
    }

    boolean retained() {
        return input.isNonConsumable();
    }

    CraftingIngredientIdentity identity() {
        return new CraftingIngredientIdentity(input.isOreDict() ? OreDictionary.getOreName(input.getOreDict()) : null,
                input.getMaterialIdentity());
    }
}

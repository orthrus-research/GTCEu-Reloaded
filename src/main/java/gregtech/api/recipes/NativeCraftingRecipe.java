package gregtech.api.recipes;

import gregtech.api.recipes.definition.CraftingDefinition;
import gregtech.api.recipes.definition.ItemValue;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.IForgeRegistryEntry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native execution of a prevalidated definition, without authoring-engine callbacks. */
abstract class NativeCraftingRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    protected final List<NativeCraftingIngredient> inputs;
    private final ItemValue output;
    private final String group;

    NativeCraftingRecipe(CraftingDefinition definition, List<NativeCraftingIngredient> inputs) {
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.output = definition.output();
        this.group = definition.group();
        setRegistryName(definition.name());
    }

    /** Maps each occupied inventory slot to its ingredient; empty slots are -1. */
    protected abstract int @Nullable [] match(InventoryCrafting inventory);

    @Override
    public boolean matches(@NotNull InventoryCrafting inventory, @NotNull World world) {
        return match(inventory) != null;
    }

    @Override
    public @NotNull ItemStack getCraftingResult(@NotNull InventoryCrafting inventory) {
        return output.stack();
    }

    @Override
    public @NotNull ItemStack getRecipeOutput() {
        return output.stack();
    }

    @Override
    public @NotNull String getGroup() {
        return group;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        for (var input : inputs) result.add(input == null ? Ingredient.EMPTY : input);
        return result;
    }

    @Override
    public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull InventoryCrafting inventory) {
        var result = NonNullList.withSize(inventory.getSizeInventory(), ItemStack.EMPTY);
        var matched = match(inventory);
        if (matched == null) return result;
        for (int slot = 0; slot < matched.length; slot++) {
            if (matched[slot] < 0) continue;
            var stack = inventory.getStackInSlot(slot).copy();
            stack.setCount(1);
            result.set(slot, inputs.get(matched[slot]).retained() ? stack : ForgeHooks.getContainerItem(stack));
        }
        return result;
    }
}

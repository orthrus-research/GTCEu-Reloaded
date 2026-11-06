package gregtech.api.recipes;

import gregtech.api.recipes.definition.ShapelessCraftingDefinition;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.RecipeMatcher;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class NativeShapelessCraftingRecipe extends NativeCraftingRecipe {

    NativeShapelessCraftingRecipe(ShapelessCraftingDefinition definition, List<NativeCraftingIngredient> inputs) {
        super(definition, inputs);
    }

    @Override
    public boolean canFit(int width, int height) {
        return width > 0 && height > 0 && (long) width * height >= inputs.size();
    }

    @Override
    protected int @Nullable [] match(InventoryCrafting inventory) {
        List<ItemStack> stacks = new ArrayList<>(inputs.size());
        int[] slots = new int[inputs.size()];
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (stacks.size() == inputs.size()) return null;
            slots[stacks.size()] = slot;
            stacks.add(stack);
        }
        if (stacks.size() != inputs.size()) return null;
        var paired = RecipeMatcher.findMatches(stacks, inputs);
        if (paired == null) return null;
        int[] mapping = new int[inventory.getSizeInventory()];
        Arrays.fill(mapping, -1);
        for (int i = 0; i < paired.length; i++) mapping[slots[i]] = paired[i];
        return mapping;
    }
}

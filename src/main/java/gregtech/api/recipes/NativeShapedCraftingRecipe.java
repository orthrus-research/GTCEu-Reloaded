package gregtech.api.recipes;

import gregtech.api.recipes.definition.ShapedCraftingDefinition;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraftforge.common.crafting.IShapedRecipe;

import org.jetbrains.annotations.Nullable;

import java.util.List;

final class NativeShapedCraftingRecipe extends NativeCraftingRecipe implements IShapedRecipe {

    private final int width;
    private final int height;
    private final boolean mirrored;

    NativeShapedCraftingRecipe(ShapedCraftingDefinition definition, List<NativeCraftingIngredient> inputs) {
        super(definition, inputs);
        this.width = definition.width();
        this.height = definition.height();
        this.mirrored = definition.mirrored();
    }

    @Override
    public int getRecipeWidth() {
        return width;
    }

    @Override
    public int getRecipeHeight() {
        return height;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width >= this.width && height >= this.height;
    }

    @Override
    protected int @Nullable [] match(InventoryCrafting inventory) {
        if (!canFit(inventory.getWidth(), inventory.getHeight())) return null;
        int[] mapping = new int[inventory.getSizeInventory()];
        for (int y = 0; y <= inventory.getHeight() - height; y++) {
            for (int x = 0; x <= inventory.getWidth() - width; x++) {
                if (matchAt(inventory, x, y, false, mapping) ||
                        (mirrored && matchAt(inventory, x, y, true, mapping)))
                    return mapping;
            }
        }
        return null;
    }

    private boolean matchAt(InventoryCrafting inventory, int offsetX, int offsetY, boolean reverse, int[] mapping) {
        for (int y = 0; y < inventory.getHeight(); y++) {
            for (int x = 0; x < inventory.getWidth(); x++) {
                int localX = x - offsetX;
                int localY = y - offsetY;
                int index = localX >= 0 && localY >= 0 && localX < width && localY < height ?
                        localY * width + (reverse ? width - localX - 1 : localX) : -1;
                var ingredient = index < 0 ? null : inputs.get(index);
                var stack = inventory.getStackInRowAndColumn(x, y);
                if (ingredient == null ? !stack.isEmpty() : !ingredient.apply(stack)) return false;
                mapping[x + y * inventory.getWidth()] = ingredient == null ? -1 : index;
            }
        }
        return true;
    }
}

package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.FurnaceRecipeDefinition;
import gregtech.api.recipes.definition.ItemValue;

import net.minecraft.item.ItemStack;

import java.util.Map;

record OwnedFurnaceRecipe(FurnaceRecipeDefinition definition, SourceOrigin origin, ItemStack input, ItemStack output) {

    OwnedFurnaceRecipe(FurnaceRecipeDefinition definition, SourceOrigin origin) {
        this(definition, origin, definition.input().stack(), definition.output().stack());
    }

    void verify(Map<ItemStack, ItemStack> recipes) {
        if (recipes.get(input) != output || !definition.input().equals(ItemValue.of(input)) ||
                !definition.output().equals(ItemValue.of(output))) {
            throw new IllegalStateException("An owned furnace recipe changed outside its registration group");
        }
        if (recipes.keySet().stream().anyMatch(key -> key != input && RecipeFurnaceOwnership.overlaps(key, input))) {
            throw new IllegalStateException("An outside furnace recipe overlaps an owned input");
        }
    }
}

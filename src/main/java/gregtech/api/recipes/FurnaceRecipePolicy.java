package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.ItemValue;

import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit pack-authored exceptions to GT's blast-metal furnace cleanup, independent of script engine internals. */
public final class FurnaceRecipePolicy {

    private static final Map<ProtectedRecipe, SourceOrigin> protectedRecipes = new HashMap<>();

    private FurnaceRecipePolicy() {}

    public static void protect(ItemStack input, ItemStack output, SourceOrigin origin) {
        protectedRecipes.put(key(input, output), Objects.requireNonNull(origin, "origin"));
    }

    public static void unprotect(ItemStack input, ItemStack output) {
        protectedRecipes.remove(key(input, output));
    }

    public static boolean isProtected(ItemStack input, ItemStack output) {
        return protectedRecipes.containsKey(key(input, output));
    }

    private static ProtectedRecipe key(ItemStack input, ItemStack output) {
        if (input.isEmpty() || output.isEmpty())
            throw new IllegalArgumentException("A furnace exception needs nonempty input and output");
        ItemStack normalized = input.copy();
        normalized.setCount(1);
        return new ProtectedRecipe(ItemValue.of(normalized), ItemValue.of(output));
    }

    private record ProtectedRecipe(ItemValue input, ItemValue output) {}
}

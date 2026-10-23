package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.nbtmatch.ListNBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Keeps builtin item acceptance tables and mutable NBT requirements away from registered input objects. */
final class RecipeModifierIngredientCopies {

    private RecipeModifierIngredientCopies() {}

    static GTRecipeInput copy(GTRecipeInput input, int amount) {
        GTRecipeInput copy;
        if (input.getClass() == GTRecipeItemInput.class) {
            // The builtin constructor retains its arguments in the acceptance table. Own them before construction.
            ItemStack[] original = input.getInputStacks();
            ItemStack[] owned = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) owned[i] = original[i].copy();
            copy = new GTRecipeItemInput(owned, amount);
            if (input.isNonConsumable()) copy = copy.setNonConsumable();
        } else {
            copy = input.copyWithAmount(amount);
        }
        if (copy == input) throw new UnsupportedOperationException("Ingredient copy shares its source instance");
        if (input.hasNBTMatchingCondition())
            copy = copy.setNBTMatchingCondition(input.getNBTMatcher(),
                    copyCondition(input.getNBTMatchingCondition(), 0));
        return copy;
    }

    private static NBTCondition copyCondition(NBTCondition condition, int depth) {
        if (condition == null || condition == NBTCondition.ANY) return condition;
        if (depth > 16) throw new IllegalArgumentException("NBT matcher requirement is too deep");
        if (condition.getClass() == ListNBTCondition.class) {
            List<?> values = (List<?>) condition.value;
            if (values.size() > 4096) throw new IllegalArgumentException("NBT matcher list is too large");
            List<NBTBase> copy = new ArrayList<>();
            for (Object value : values) copy.add(((NBTBase) value).copy());
            return ListNBTCondition.create(((ListNBTCondition) condition).listTagType, condition.nbtKey, copy);
        }
        if (condition.getClass() != NBTCondition.class)
            throw new UnsupportedOperationException("Unknown NBT matcher requirement copy contract");
        Object value = condition.value;
        if (value instanceof NBTBase) value = ((NBTBase) value).copy();
        else if (value instanceof NBTCondition) value = copyCondition((NBTCondition) value, depth + 1);
        else if (!(value instanceof String || value instanceof Boolean || value instanceof Byte ||
                value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float ||
                value instanceof Double || value instanceof BigInteger || value instanceof BigDecimal))
            // Legacy array matchers use object identity; copying an array would silently change their semantics.
            throw new UnsupportedOperationException("Unknown or identity-based NBT matcher value cannot be owned");
        return NBTCondition.create(condition.tagType, condition.nbtKey, value);
    }
}

package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;

import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;

/** Alternatives share one explicit NBT/capability policy. Inventory quantity never weakens either policy. */
public record ItemIngredient(List<ItemValue> alternatives, int amount, boolean consumable, NbtPolicy nbt,
                             CapabilityPolicy capabilities)
        implements ItemInput {

    public ItemIngredient {
        alternatives = List.copyOf(alternatives);
        Objects.requireNonNull(nbt, "nbt");
        Objects.requireNonNull(capabilities, "capabilities");
    }

    public static ItemIngredient exact(ItemStack stack) {
        return new ItemIngredient(List.of(ItemValue.of(stack)), stack.getCount(), true,
                NbtPolicy.exact(stack.getTagCompound()), CapabilityPolicy.MATCH);
    }

    /** Produces a native ingredient. No scripting engine, closure or reflective dispatch is retained. */
    public GTRecipeInput compile() {
        if (amount <= 0 || alternatives.isEmpty() || alternatives.stream().anyMatch(ItemValue::isEmpty)) {
            throw new IllegalArgumentException("An ingredient needs a positive amount and nonempty alternatives");
        }
        ItemStack[] stacks = alternatives.stream().map(ItemValue::stack).toArray(ItemStack[]::new);
        GTRecipeInput result = new GTRecipeItemInput(stacks, amount)
                .setNBTMatchingCondition(new ItemPolicyMatcher(alternatives, nbt, capabilities), null);
        return consumable ? result : result.setNonConsumable();
    }
}

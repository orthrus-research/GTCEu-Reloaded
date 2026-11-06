package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;

import java.util.Objects;

/** Ore membership follows the native ore dictionary; NBT matching is an explicit additional constraint. */
public record OreIngredient(String oreName, int amount, boolean consumable, NbtPolicy nbt) implements ItemInput {

    public OreIngredient {
        Objects.requireNonNull(oreName, "oreName");
        Objects.requireNonNull(nbt, "nbt");
    }

    @Override
    public GTRecipeInput compile() {
        if (oreName.isBlank() || amount <= 0) throw new IllegalArgumentException("Invalid ore ingredient");
        GTRecipeInput input = new GTRecipeOreInput(oreName, amount);
        if (nbt != NbtPolicy.any()) input = input.setNBTMatchingCondition(new PolicyNbtMatcher(nbt), null);
        return consumable ? input : input.setNonConsumable();
    }
}

package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTUtility;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Objects;

/** Live ore membership restricted to a qualified material identity. */
public record MaterialIngredient(OrePrefix prefix, ResourceLocation material, int amount, boolean consumable,
                                 NbtPolicy nbt)
        implements ItemInput {

    public MaterialIngredient {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(nbt, "nbt");
    }

    public MaterialIngredient(OrePrefix prefix, Material material, int amount, boolean consumable, NbtPolicy nbt) {
        this(prefix, material.getResourceLocation(), amount, consumable, nbt);
    }

    public String oreName() {
        return prefix.name() + GTUtility.lowerUnderscoreToUpperCamel(material.getPath());
    }

    @Override
    public GTRecipeInput compile() {
        if (amount <= 0 || !OreDictionary.doesOreNameExist(oreName()))
            throw new IllegalArgumentException(
                    "Material ingredient must have a positive amount and a registered ore: " +
                            material + " / " + oreName());
        GTRecipeInput input = new GTRecipeOreInput(oreName(), material, amount);
        if (nbt != NbtPolicy.any()) input = input.setNBTMatchingCondition(new PolicyNbtMatcher(nbt), null);
        return consumable ? input : input.setNonConsumable();
    }
}

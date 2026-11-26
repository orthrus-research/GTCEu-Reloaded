package gregtech.integration.groovy;

import gregtech.api.fluids.FluidBuilder;
import gregtech.api.fluids.attribute.FluidAttributes;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.Element;
import gregtech.api.unification.Elements;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.event.MaterialEvent;
import gregtech.api.unification.material.properties.ExtraToolProperty;
import gregtech.api.unification.material.properties.MaterialToolProperty;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.api.IIngredient;
import com.cleanroommc.groovyscript.helper.ingredient.OreDictIngredient;
import com.cleanroommc.groovyscript.sandbox.expand.ExpansionHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class GroovyExpansions {

    public static <R extends RecipeBuilder<R>> RecipeBuilder<R> property(RecipeBuilder<R> builder, String key,
                                                                         Object value) {
        if (!builder.applyNamedProperty(key, value)) {
            GroovyLog.get().error("Failed to add property '{}' with '{}' to recipe", key, value);
        }
        return builder;
    }

    private static final Set<Class<?>> INGREDIENT_INPUT_BUILDERS = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Lets scripts pass GroovyScript ingredients (item, ore and fluid ingredients and their lists) to the
     * {@code inputs} and {@code notConsumable} of builders of this class. These are expansions rather than
     * RecipeBuilder methods, so the published RecipeBuilder names no GroovyScript type. Each builder class gets its
     * own: Groovy reaches a method mixed into a superclass only through its missing-method path, which does not
     * spread varargs.
     */
    static synchronized void mixinIngredientInputs(Class<?> builderType) {
        if (!INGREDIENT_INPUT_BUILDERS.add(builderType)) return;
        ExpansionHelper.mixinMethod(builderType, GroovyExpansions.class, "inputs");
        ExpansionHelper.mixinMethod(builderType, GroovyExpansions.class, "notConsumable");
    }

    public static <R extends RecipeBuilder<R>> R inputs(R builder, IIngredient ingredient) {
        return builder.input(ofGroovyIngredient(ingredient));
    }

    public static <R extends RecipeBuilder<R>> R inputs(R builder, IIngredient... ingredients) {
        for (IIngredient ingredient : ingredients) {
            builder.input(ofGroovyIngredient(ingredient));
        }
        return builder;
    }

    public static <R extends RecipeBuilder<R>> R inputs(R builder, Collection<IIngredient> ingredients) {
        for (IIngredient ingredient : ingredients) {
            builder.input(ofGroovyIngredient(ingredient));
        }
        return builder;
    }

    public static <R extends RecipeBuilder<R>> R notConsumable(R builder, IIngredient ingredient) {
        return builder.notConsumable(ofGroovyIngredient(ingredient));
    }

    private static GTRecipeInput ofGroovyIngredient(IIngredient ingredient) {
        if (ingredient instanceof OreDictIngredient) {
            return new GTRecipeOreInput(((OreDictIngredient) ingredient).getOreDict(), ingredient.getAmount());
        }
        Object oIngredient = ingredient;
        if (oIngredient instanceof ItemStack) {
            return new GTRecipeItemInput((ItemStack) oIngredient);
        }
        if (ingredient instanceof FluidStack) {
            return new GTRecipeFluidInput((FluidStack) ingredient);
        }
        throw new IllegalArgumentException("Could not add groovy ingredient " + ingredient + " to recipe!");
    }

    public static Material.Builder materialBuilder(MaterialEvent event, int id, ResourceLocation resourceLocation) {
        return Material.builder(id, resourceLocation);
    }

    public static Material.Builder materialBuilder(MaterialEvent event, int id, String domain, String path) {
        return materialBuilder(event, id, new ResourceLocation(domain, path));
    }

    public static Material.Builder materialBuilder(MaterialEvent event, int id, String s) {
        String domain, path;
        if (s.contains(":")) {
            String[] parts = s.split(":", 2);
            domain = parts[0];
            path = parts[1];
        } else {
            domain = GroovyScript.getRunConfig().getPackId();
            path = s;
        }
        return materialBuilder(event, id, new ResourceLocation(domain, path));
    }

    public static MaterialToolProperty.Builder toolBuilder(MaterialEvent event, float harvestSpeed, float attackDamage,
                                                           int durability, int harvestLevel) {
        return MaterialToolProperty.Builder.of(harvestSpeed, attackDamage, durability, harvestLevel);
    }

    public static MaterialToolProperty.Builder toolBuilder(MaterialEvent event) {
        return toolBuilder(event, 1.0F, 1.0F, 100, 2);
    }

    public static ExtraToolProperty.Builder overrideToolBuilder(MaterialEvent event) {
        return ExtraToolProperty.Builder.of();
    }

    public static ExtraToolProperty.Builder overrideToolBuilder(MaterialEvent event, float harvestSpeed,
                                                                float attackDamage,
                                                                int durability, int harvestLevel) {
        return ExtraToolProperty.Builder.of(harvestSpeed, attackDamage, durability, harvestLevel);
    }

    public static FluidBuilder fluidBuilder(MaterialEvent event) {
        return new FluidBuilder();
    }

    public static Element addElement(MaterialEvent event, long protons, long neutrons, long halfLifeSeconds,
                                     String decayTo, String name, String symbol, boolean isIsotope) {
        return Elements.add(protons, neutrons, halfLifeSeconds, decayTo, name, symbol, isIsotope);
    }

    public static Element addElement(MaterialEvent event, long protons, long neutrons, String name, String symbol,
                                     boolean isIsotope) {
        return Elements.add(protons, neutrons, name, symbol, isIsotope);
    }

    public static Element addElement(MaterialEvent event, long protons, long neutrons, String name, String symbol) {
        return Elements.add(protons, neutrons, name, symbol);
    }

    public static FluidBuilder acidic(FluidBuilder builder) {
        return builder.attributes(FluidAttributes.ACID);
    }
}

package gregtech.integration.groovy;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.groovyscript.helper.ingredient.OreDictIngredient;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroovyRecipeExpansionsTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void scriptIngredientsReachTheBuilderOfAnyMap() {
        OreDictionary.registerOre("ingotExpansionProbe", new ItemStack(Items.IRON_INGOT));
        OreDictionary.registerOre("gemExpansionProbe", new ItemStack(Items.DIAMOND));
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("groovy_expansion_inputs",
                new SimpleRecipeBuilder()).itemInputs(4).itemOutputs(1).build();
        // GroovyScript installs this frontend, which every new recipe map tells about its builder class.
        new GroovyRecipeFrontend().mapCreated(map, SimpleRecipeBuilder.class);
        var binding = new Binding();
        binding.setVariable("builder", map.recipeBuilder());
        binding.setVariable("ingot", new OreDictIngredient("ingotExpansionProbe"));
        binding.setVariable("gem", new OreDictIngredient("gemExpansionProbe"));
        binding.setVariable("stick", new ItemStack(Items.STICK));
        Object result = new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                builder.inputs(ingot).inputs(ingot, gem).inputs([gem]).notConsumable(gem).inputs(stick)
                """);
        var builder = assertInstanceOf(SimpleRecipeBuilder.class, result);
        List<GTRecipeInput> inputs = builder.getInputs();
        assertEquals(6, inputs.size());
        for (int i = 0; i < 5; i++) assertInstanceOf(GTRecipeOreInput.class, inputs.get(i));
        assertEquals(OreDictionary.getOreID("ingotExpansionProbe"), inputs.get(0).getOreDict());
        assertEquals(OreDictionary.getOreID("gemExpansionProbe"), inputs.get(3).getOreDict());
        assertFalse(inputs.get(3).isNonConsumable());
        assertTrue(inputs.get(4).isNonConsumable());
        assertTrue(inputs.get(5).acceptsStack(new ItemStack(Items.STICK)));
    }
}

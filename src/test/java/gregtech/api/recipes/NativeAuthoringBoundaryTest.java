package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.BlastRecipeBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.unification.material.properties.BlastProperty;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeAuthoringBoundaryTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void runtimeClasspathHasNoCraftTweakerOrZenScriptAndInvalidGasTiersUseNativeDiagnostics() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("crafttweaker.CraftTweakerAPI"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("stanhebben.zenscript.ZenModule"));
        assertEquals(BlastProperty.GasTier.HIGHER, BlastProperty.validateGasTier("higher"));
        assertThrows(IllegalArgumentException.class, () -> BlastProperty.validateGasTier("not_a_gas_tier"));
    }

    @Test
    void renamedPropertyHookKeepsSpecializedBuilderDispatchFromJavaAndGroovy() {
        BlastRecipeBuilder java = new BlastRecipeBuilder();
        assertTrue(java.applyNamedProperty("temperature", 1800));
        BlastRecipeBuilder groovy = new BlastRecipeBuilder();
        Binding variables = new Binding();
        variables.setVariable("builder", groovy);
        new GroovyShell(getClass().getClassLoader(), variables).evaluate(
                "gregtech.integration.groovy.GroovyExpansions.property(builder, 'temperature', 1800)");
        assertEquals(java.getBlastFurnaceTemp(), groovy.getBlastFurnaceTemp());
        assertEquals(1800, groovy.copy().getBlastFurnaceTemp());
    }

    @Test
    void authoringProvenanceSurvivesBuilderCopiesAndNativeOutputRemovalRunsOnce() {
        var map = new RecipeMapBuilder<>("native_authoring_boundary", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(2).build();
        var builder = map.recipeBuilder().authored().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.GOLD_INGOT)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(20).EUt(8);
        Recipe recipe = builder.copy().build().getResult();
        assertTrue(recipe.isAuthoredRecipe());
        assertTrue(new SimpleRecipeBuilder(recipe, map).build().getResult().isAuthoredRecipe());
        assertTrue(map.compileRecipe(recipe));
        assertEquals(0, GTRecipeHandler.removeRecipesByOutputs(map, List.of(new ItemStack(Items.GOLD_INGOT, 2)),
                List.of(), true));
        assertEquals(1,
                GTRecipeHandler.removeRecipesByOutputs(map, List.of(new ItemStack(Items.GOLD_INGOT)), List.of(), true));
        assertNull(map.findRecipe(8, List.of(new ItemStack(Items.IRON_INGOT)), List.of()));
    }
}

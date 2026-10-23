package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.properties.impl.ConditionProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.util.EnumValidationResult;
import gregtech.integration.crafttweaker.recipe.CTRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import stanhebben.zenscript.annotations.ZenMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RecipeConditionBuilderTest {

    private static RecipeMap<SimpleRecipeBuilder> map;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        map = new RecipeMapBuilder<>("condition_builder_test", new SimpleRecipeBuilder())
                .itemInputs(8).itemOutputs(8).build();
    }

    @Test
    void repeatedBuildCopiesAndFromRecipeKeepEarlierImmutableRequirementsAndOtherProperties() {
        SimpleRecipeBuilder builder = builder().conditionBiome("minecraft:plains");
        builder.applyProperty(TemperatureProperty.getInstance(), 1500);
        Recipe first = builder.build().getResult();
        SimpleRecipeBuilder copied = builder.copy();
        builder.conditionBiome("minecraft:desert");
        Recipe second = builder.build().getResult();
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        context.biome = new ResourceLocation("minecraft", "desert");
        assertFalse(conditions(first).evaluate(context).isSuccess());
        assertTrue(conditions(second).evaluate(context).isSuccess());
        assertFalse(copied.getRecipeConditions().evaluate(context).isSuccess());
        SimpleRecipeBuilder reconstructed = new SimpleRecipeBuilder(first, map).conditionHeight(70, 90);
        context.biome = new ResourceLocation("minecraft", "plains");
        assertTrue(conditions(first).evaluate(context).isSuccess());
        assertFalse(reconstructed.getRecipeConditions().evaluate(context).isSuccess());
        assertEquals(1500, second.getProperty(TemperatureProperty.getInstance(), 0));
        assertSame(conditions(first), conditions(first.copy()));
        assertSame(conditions(first), conditions(Recipe.trimRecipeOutputs(first, map, 0, -1)));
    }

    @Test
    void parallelAppendAndRawCtPropertyCompositionKeepSourceAndBoundaries() {
        Recipe first = builder().conditionBiome("minecraft:plains").conditionBiome("minecraft:desert").build()
                .getResult();
        Recipe second = builder().conditionBiome("minecraft:desert").conditionBiome("minecraft:forest").build()
                .getResult();
        SimpleRecipeBuilder appended = map.recipeBuilder().append(first, 3, false).append(second, 2, false);
        Recipe combined = appended.build().getResult();
        assertEquals(2, conditions(combined).clauseCount());
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        assertFalse(conditions(combined).evaluate(context).isSuccess());
        context.biome = new ResourceLocation("minecraft", "desert");
        assertTrue(conditions(combined).evaluate(context).isSuccess());
        context.biome = new ResourceLocation("minecraft", "forest");
        assertFalse(conditions(combined).evaluate(context).isSuccess());
        SimpleRecipeBuilder raw = builder();
        assertTrue(raw.applyPropertyCT(ConditionProperty.KEY, conditions(first)));
        assertTrue(raw.applyPropertyCT(ConditionProperty.KEY, conditions(second)));
        assertFalse(raw.getRecipeConditions().evaluate(context).isSuccess());
        assertFalse(raw.applyPropertyCT(ConditionProperty.KEY, "invalid"));
        assertFalse(conditions(first).evaluate(context).isSuccess());
    }

    @Test
    void propertyStorageStillRejectsDuplicatesAndRoundTripsTheRegisteredImmutableProperty() {
        RecipePropertyStorageImpl storage = new RecipePropertyStorageImpl();
        RecipeConditionSet set = RecipeConditionSet.EMPTY.add(RecipeConditions.daytime(false));
        assertTrue(storage.store(ConditionProperty.getInstance(), set));
        assertFalse(storage.store(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY));
        assertSame(set, storage.copy().get(ConditionProperty.getInstance(), null));
        RecipePropertyStorageImpl loaded = new RecipePropertyStorageImpl();
        loaded.deserializeNBT(storage.serializeNBT());
        assertEquals(set.serialize(), loaded.get(ConditionProperty.getInstance(), null).serialize());
    }

    @Test
    void craftTweakerMethodsAreActuallyExposedAndUseTheSameValidationPath() throws Exception {
        CTRecipeBuilder ct = new CTRecipeBuilder(builder());
        ct.conditionBiome("minecraft:plains").conditionHeight(1, 100).conditionDaytime()
                .conditionTime(0, 12000).conditionRain(0).conditionThunder(1, true)
                .conditionAdjacentBlocks(new String[][] { { "minecraft:air" } });
        assertEquals(7, ct.backingBuilder.getRecipeConditions().clauseCount());
        assertTrue(ct.backingBuilder.getRecipeConditions().evaluate(new TestRecipeConditionContext()).isSuccess());
        for (Method method : CTRecipeBuilder.class.getDeclaredMethods()) {
            if (method.getName().startsWith("condition")) assertNotNull(method.getAnnotation(ZenMethod.class));
        }
        CTRecipeBuilder invalid = new CTRecipeBuilder(builder());
        assertThrows(IllegalArgumentException.class, () -> invalid.conditionRain(Float.NaN));
        assertEquals(EnumValidationResult.INVALID, invalid.backingBuilder.build().getType());
        assertThrows(IllegalArgumentException.class, () -> builder().conditionHeight(10, 0));
    }

    @Test
    void groovyResolvesThePublicBuilderConveniencesAndNestedStringArrays() {
        SimpleRecipeBuilder builder = builder();
        Binding binding = new Binding();
        binding.setVariable("builder", builder);
        Object result = new GroovyShell(getClass().getClassLoader(), binding).evaluate(
                "builder.conditionBiome('minecraft:plains').conditionBiome('minecraft:desert')" +
                        ".conditionHeight(0, 100).conditionDaytime().conditionTime(0, 12000)" +
                        ".conditionRain(0.0f).conditionThunder(1.0f, true)" +
                        ".conditionAdjacentBlocks([['minecraft:air']] as String[][])");
        assertSame(builder, result);
        assertEquals(7, builder.getRecipeConditions().clauseCount());
        assertTrue(builder.getRecipeConditions().evaluate(new TestRecipeConditionContext()).isSuccess());
    }

    private static SimpleRecipeBuilder builder() {
        return map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.GOLD_INGOT))
                .EUt(8).duration(40);
    }

    private static RecipeConditionSet conditions(Recipe recipe) {
        return recipe.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY);
    }
}

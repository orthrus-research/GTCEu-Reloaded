package gregtech.api.recipes.definition;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeDefinitionCompiler;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.BlastRecipeBuilder;
import gregtech.api.recipes.builders.ComputationRecipeBuilder;
import gregtech.api.recipes.builders.FusionRecipeBuilder;
import gregtech.api.recipes.builders.ImplosionRecipeBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;
import gregtech.api.recipes.properties.impl.CleanroomProperty;
import gregtech.api.recipes.properties.impl.ComputationProperty;
import gregtech.api.recipes.properties.impl.ConditionProperty;
import gregtech.api.recipes.properties.impl.DimensionProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.recipes.properties.impl.ImplosionExplosiveProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.recipes.properties.impl.TotalComputationProperty;
import gregtech.common.ConfigHolder;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeAttributeTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final SourceOrigin SOURCE = new SourceOrigin("java", "PackRecipes.java", 20, 1, "special",
            "recipes");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void specializedValuesUseNativePropertiesAndPreserveLongFusionEnergy() {
        var blast = map(new BlastRecipeBuilder(), 1);
        Recipe recipe = compile(definition(blast).withAttributes(new RecipeAttribute.Temperature(1800)));
        assertEquals(1800, recipe.getProperty(TemperatureProperty.getInstance(), 0));
        var fusion = map(new FusionRecipeBuilder(), 1);
        long energy = 9_000_000_000L;
        Recipe fused = compile(definition(fusion).withAttributes(new RecipeAttribute.FusionStart(energy)));
        assertEquals(energy, fused.getProperty(FusionEUToStartProperty.getInstance(), 0L));
        var computation = map(new ComputationRecipeBuilder(), 1);
        Recipe computed = compile(definition(computation).withAttributes(new RecipeAttribute.ComputationRate(16),
                new RecipeAttribute.TotalComputation(40)));
        assertEquals(16, computed.getProperty(ComputationProperty.getInstance(), 0));
        assertEquals(40, computed.getProperty(TotalComputationProperty.getInstance(), 0));
        assertEquals(40, computed.getDuration());
        assertTrue(blast.getRecipeList().isEmpty());
        assertTrue(fusion.getRecipeList().isEmpty());
        assertTrue(computation.getRecipeList().isEmpty());
    }

    @Test
    void implosionAddsOwnedExplosivesAndChecksTheExpandedInputLimit() {
        ItemStack tnt = new ItemStack(Blocks.TNT, 4);
        RecipeAttribute.Explosives explosives = new RecipeAttribute.Explosives(ItemValue.of(tnt));
        tnt.setCount(64);
        Recipe recipe = compile(definition(map(new ImplosionRecipeBuilder(), 2)).withAttributes(explosives));
        assertEquals(2, recipe.getInputs().size());
        assertEquals(4, recipe.getProperty(ImplosionExplosiveProperty.getInstance(), ItemStack.EMPTY).getCount());
        var inventory = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Blocks.TNT, 5)));
        assertTrue(recipe.matches(true, inventory, List.of()));
        assertTrue(inventory.getFirst().isEmpty());
        assertEquals(1, inventory.get(1).getCount());
        var rejected = RecipeDefinitionCompiler.compile(
                definition(map(new ImplosionRecipeBuilder(), 1)).withAttributes(explosives));
        assertTrue(rejected.value().isEmpty());
        assertEquals("recipe.inputs.expanded_limit", rejected.diagnostics().getFirst().code());
    }

    @Test
    void commonAttributesOwnDimensionsAndKeepNativeCleanroomConfigurationBehavior() {
        var map = map(new SimpleRecipeBuilder(), 1);
        var dimensions = new ArrayList<>(List.of(0, 1));
        RecipeConditionSet conditions = RecipeConditionSet.EMPTY.add(RecipeConditions.daytime(false));
        RecipeDefinition declaration = definition(map).withAttributes(
                new RecipeAttribute.Dimensions(dimensions, List.of(-1)),
                new RecipeAttribute.Conditions(conditions), new RecipeAttribute.Cleanroom("cleanroom"));
        dimensions.clear();
        boolean previous = ConfigHolder.machines.enableCleanroom;
        try {
            ConfigHolder.machines.enableCleanroom = true;
            Recipe recipe = compile(declaration);
            var ids = recipe.getProperty(DimensionProperty.getInstance(), null);
            assertTrue(ids.checkDimension(0));
            assertTrue(ids.checkDimension(1));
            assertFalse(ids.checkDimension(-1));
            assertSame(CleanroomType.CLEANROOM, recipe.getProperty(CleanroomProperty.getInstance(), null));
            assertEquals(conditions.serialize(), recipe.getProperty(ConditionProperty.getInstance(), null).serialize());
            ids.whiteListDimensions.clear();
            assertTrue(compile(declaration).getProperty(DimensionProperty.getInstance(), null).checkDimension(0),
                    "Compiled native property mutation must not mutate the descriptor");
            ConfigHolder.machines.enableCleanroom = false;
            assertNull(compile(declaration).getProperty(CleanroomProperty.getInstance(), null));
        } finally {
            ConfigHolder.machines.enableCleanroom = previous;
        }
    }

    @Test
    void invalidPropertiesReportPathsWithoutRegistrationOrSilentLastWriteWins() {
        var map = map(new SimpleRecipeBuilder(), 1);
        var result = RecipeDefinitionCompiler.compile(definition(map).withAttributes(
                new RecipeAttribute.Temperature(1800), new RecipeAttribute.Temperature(2000),
                new RecipeAttribute.Cleanroom("missing_cleanroom"),
                new RecipeAttribute.Dimensions(List.of(0), List.of(0))));
        assertTrue(result.value().isEmpty());
        assertEquals(List.of("recipe.attribute.builder", "recipe.attribute.duplicate", "recipe.cleanroom.unknown",
                "recipe.dimensions.conflict"), result.diagnostics().stream().map(d -> d.code()).toList());
        assertEquals("attributes[2].name", result.diagnostics().get(2).field());
        assertTrue(map.getRecipeList().isEmpty());
        var computation = map(new ComputationRecipeBuilder(), 1);
        var mismatch = RecipeDefinitionCompiler.compile(definition(computation).withAttributes(
                new RecipeAttribute.ComputationRate(-1), new RecipeAttribute.TotalComputation(100)));
        assertEquals(List.of("recipe.attribute.range", "recipe.computation.duration"),
                mismatch.diagnostics().stream().map(d -> d.code()).toList());
    }

    private static Recipe compile(RecipeDefinition definition) {
        var result = RecipeDefinitionCompiler.compile(definition);
        assertTrue(result.value().isPresent(), () -> result.diagnostics().toString());
        return result.value().orElseThrow();
    }

    private static <R extends RecipeBuilder<R>> RecipeMap<R> map(R builder, int inputs) {
        return new RecipeMapBuilder<>("attribute_definition_" + IDS.incrementAndGet(), builder)
                .itemInputs(inputs).itemOutputs(1).build();
    }

    private static RecipeDefinition definition(RecipeMap<?> map) {
        return new RecipeDefinition(SOURCE, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.IRON_INGOT))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 40, 8, false);
    }
}

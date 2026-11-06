package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecyclingMaterial;
import gregtech.api.recipes.definition.RecyclingRecipeDefinition;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.loaders.recipe.RecyclingRecipes;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecyclingRecipePreparationTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static RecyclingUnificationFixture unification;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.perform();
        unification = new RecyclingUnificationFixture();
    }

    @AfterAll
    static void restore() throws Exception {
        if (unification != null) unification.close();
    }

    @Test
    void preparationDoesNotPublishAndMatchesNativeGeneration() {
        var definition = definition(input(), 2 * GTValues.M);
        var maps = List.of(RecipeMaps.MACERATOR_RECIPES, RecipeMaps.EXTRACTOR_RECIPES, RecipeMaps.ARC_FURNACE_RECIPES);
        var revisions = maps.stream().map(RecipeMap::getRecipeRevision).toList();
        var prepared = RecyclingRecipeCompiler.prepare(definition).value().orElseThrow();
        assertEquals(3, prepared.size());
        assertEquals(revisions, maps.stream().map(RecipeMap::getRecipeRevision).toList());
        RecyclingRecipes.registerRecyclingRecipes(definition.input().stack(),
                definition.materials().stream().map(value -> new MaterialStack(value.material(), value.amount()))
                        .toList(),
                false, definition.prefix());
        try {
            for (var expected : prepared) {
                var actual = find(expected.map(), expected.recipe());
                assertNotNull(actual);
                assertEquals(expected.recipe().getDuration(), actual.getDuration());
                assertEquals(expected.recipe().getEUt(), actual.getEUt());
                assertEquals(expected.recipe().getRecipeCategory(), actual.getRecipeCategory());
                assertEquals(expected.recipe().getOutputs().size(), actual.getOutputs().size());
                for (int i = 0; i < actual.getOutputs().size(); i++) {
                    assertTrue(ItemStack.areItemStacksEqual(expected.recipe().getOutputs().get(i),
                            actual.getOutputs().get(i)));
                }
                assertEquals(expected.recipe().getFluidOutputs().size(), actual.getFluidOutputs().size());
                for (int i = 0; i < actual.getFluidOutputs().size(); i++) {
                    assertTrue(expected.recipe().getFluidOutputs().get(i)
                            .isFluidStackIdentical(actual.getFluidOutputs().get(i)));
                }
            }
        } finally {
            for (var expected : prepared) {
                var actual = find(expected.map(), expected.recipe());
                if (actual != null) expected.map().removeRecipe(actual);
            }
        }
    }

    @Test
    void groupReplacementRemovalAndEmptyCompositionRemainTransactional() {
        var input = input();
        var original = definition(input, GTValues.M);
        var registrar = new RecipeDefinitionRegistrar();
        try {
            assertEquals(3, registrar.replaceRecycling("recovery", List.of(original)).value().orElseThrow().recipes());
            var first = RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of());
            assertNotNull(first);
            assertEquals(3, registrar.replaceRecycling("recovery", List.of(definition(input, 2 * GTValues.M)))
                    .value().orElseThrow().recipes());
            assertNotSame(first, RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of()));
            var empty = new RecyclingRecipeDefinition(original.origin(), original.input(), List.of(), false,
                    original.prefix());
            assertEquals(0, registrar.replaceRecycling("recovery", List.of(empty)).value().orElseThrow().recipes());
            assertNull(RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of()));
            assertEquals(3, registrar.replaceRecycling("recovery", List.of(original)).value().orElseThrow().recipes());
            assertEquals(0, registrar.replaceRecycling("recovery", List.of()).value().orElseThrow().recipes());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void conflictInOneGeneratedMapKeepsAllOldRecipesAndPublishesNoneOfTheReplacement() {
        var original = definition(input(), GTValues.M);
        var replacement = definition(input(), GTValues.M);
        var registrar = new RecipeDefinitionRegistrar();
        assertTrue(registrar.replaceRecycling("recovery", List.of(original)).value().isPresent());
        var previous = RecyclingRecipeCompiler.prepare(original).value().orElseThrow().stream()
                .map(value -> new NativeRecipeRegistration(value.map(), find(value.map(), value.recipe()))).toList();
        var blocker = RecipeMaps.ARC_FURNACE_RECIPES.recipeBuilder().inputs(replacement.input().stack())
                .outputs(new ItemStack(Items.COAL)).duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        try {
            var failed = registrar.replaceRecycling("recovery", List.of(replacement));
            assertTrue(failed.value().isEmpty());
            assertEquals("recipe.index.conflict", failed.diagnostics().getFirst().code());
            for (var entry : previous) assertSame(entry.recipe(), find(entry.map(), entry.recipe()));
            assertNull(RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(replacement.input().stack()),
                    List.of()));
            assertNull(RecipeMaps.EXTRACTOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(replacement.input().stack()),
                    List.of()));
        } finally {
            blocker.map().removeRecipe(blocker.recipe());
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void descriptorOwnsInputsAndInvalidQuantitiesCarrySourceWithoutChangingTheGroup() {
        var stack = input();
        var values = new ArrayList<>(List.of(new RecyclingMaterial(Materials.Iron, GTValues.M)));
        var definition = new RecyclingRecipeDefinition(origin(), ItemValue.of(stack), values, true, null);
        stack.setCount(32);
        values.clear();
        assertEquals(1, definition.input().stack().getCount());
        assertEquals(1, definition.materials().size());
        var registrar = new RecipeDefinitionRegistrar();
        try {
            assertTrue(registrar.replaceRecycling("recovery", List.of(definition)).value().isPresent());
            var current = RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(definition.input().stack()),
                    List.of());
            assertNotNull(current);
            for (long amount : new long[] { 0, -1, Long.MAX_VALUE }) {
                var invalid = new RecyclingRecipeDefinition(definition.origin(), definition.input(),
                        List.of(new RecyclingMaterial(Materials.Iron, amount)), false, null);
                var result = registrar.replaceRecycling("recovery", List.of(invalid));
                assertTrue(result.value().isEmpty());
                assertEquals(definition.origin(), result.diagnostics().getFirst().origin());
                assertSame(current, RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE,
                        List.of(definition.input().stack()), List.of()));
            }
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    private static RecyclingRecipeDefinition definition(ItemStack input, long amount) {
        return new RecyclingRecipeDefinition(origin(), ItemValue.of(input),
                List.of(new RecyclingMaterial(Materials.Iron, amount),
                        new RecyclingMaterial(Materials.Copper, GTValues.M)),
                false, OrePrefix.toolHeadDrill);
    }

    @Test
    void adoptedNativeRecipesSurviveFailedReplacementAndReturnAfterDisableAndClear() {
        var original = definition(input(), GTValues.M);
        var baselines = registerBaseline(original);
        var registrar = new RecipeDefinitionRegistrar();
        try {
            var invalid = definition(original.input().stack(), -1);
            assertTrue(registrar.replaceRecycling("recovery", List.of(invalid), baselines).value().isEmpty());
            assertTrue(registrar.groups().isEmpty());
            baselines.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));

            assertEquals(3, registrar.replaceRecycling("recovery",
                    List.of(definition(original.input().stack(), 3 * GTValues.M)), baselines)
                    .value().orElseThrow().recipes());
            baselines.forEach(value -> assertNotSame(value.recipe(), find(value.map(), value.recipe())));
            assertTrue(registrar.replaceRecycling("recovery", List.of(original), baselines).value().isEmpty());
            assertTrue(registrar.replaceRecycling("recovery", List.of()).value().isPresent());
            baselines.forEach(value -> assertNull(find(value.map(), value.recipe())));
            assertEquals(List.of("recovery"), registrar.groups(), "Disabled group retains baseline ownership");
            assertTrue(registrar.replaceRecycling("recovery", List.of(original)).value().isPresent());
            assertTrue(registrar.replaceRecycling("recovery", List.of()).value().isPresent());
            assertEquals(0, registrar.clear().value().orElseThrow());
            baselines.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));
            assertTrue(registrar.groups().isEmpty());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            baselines.forEach(value -> value.map().removeRecipe(value.recipe()));
        }
    }

    @Test
    void adoptionAndRestorationConflictsDoNotPartiallyChangeAnyMap() {
        var original = definition(input(), GTValues.M);
        var next = definition(input(), 2 * GTValues.M);
        var baselines = registerBaseline(original);
        var blocker = RecipeMaps.ARC_FURNACE_RECIPES.recipeBuilder().inputs(next.input().stack())
                .outputs(new ItemStack(Items.COAL)).duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        var registrar = new RecipeDefinitionRegistrar();
        NativeRecipeRegistration restorationBlocker = null;
        try {
            assertTrue(registrar.replaceRecycling("recovery", List.of(next), baselines).value().isEmpty());
            baselines.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));
            assertTrue(registrar.groups().isEmpty());
            blocker.map().removeRecipe(blocker.recipe());
            assertTrue(registrar.replaceRecycling("recovery", List.of(next), baselines).value().isPresent());
            var active = RecyclingRecipeCompiler.prepare(next).value().orElseThrow().stream()
                    .map(value -> new NativeRecipeRegistration(value.map(), find(value.map(), value.recipe())))
                    .toList();
            restorationBlocker = RecipeMaps.ARC_FURNACE_RECIPES.recipeBuilder().inputs(original.input().stack())
                    .outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8)
                    .buildAndRegisterWithReceipt().orElseThrow();
            assertTrue(registrar.clear().value().isEmpty());
            active.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));
            assertNull(RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE,
                    List.of(original.input().stack()), List.of()));
            restorationBlocker.map().removeRecipe(restorationBlocker.recipe());
            assertTrue(registrar.clear().value().isPresent());
            baselines.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));
            active.forEach(value -> assertNull(find(value.map(), value.recipe())));
        } finally {
            blocker.map().removeRecipe(blocker.recipe());
            if (restorationBlocker != null) restorationBlocker.map().removeRecipe(restorationBlocker.recipe());
            assertTrue(registrar.clear().value().isPresent());
            baselines.forEach(value -> value.map().removeRecipe(value.recipe()));
        }
    }

    @Test
    void adoptionRequiresExactPresentIdentitiesAndRejectsDuplicateAndOwnedReceipts() {
        var original = definition(input(), GTValues.M);
        var baselines = registerBaseline(original);
        var registrar = new RecipeDefinitionRegistrar();
        try {
            assertTrue(registrar.replaceRecycling("recovery", List.of(),
                    List.of(baselines.getFirst(), baselines.getFirst())).value().isEmpty());
            var copied = RecyclingRecipeCompiler.prepare(original).value().orElseThrow().getFirst();
            assertTrue(registrar.replaceRecycling("recovery", List.of(),
                    List.of(new NativeRecipeRegistration(copied.map(), copied.recipe()))).value().isEmpty());
            assertTrue(registrar.groups().isEmpty());
            baselines.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));
            assertTrue(registrar.replaceRecycling("recovery", List.of(original), baselines).value().isPresent());
            var owned = new NativeRecipeRegistration(baselines.getFirst().map(),
                    find(baselines.getFirst().map(), baselines.getFirst().recipe()));
            assertEquals("recipe.baseline.identity", registrar.replaceRecycling("other", List.of(), List.of(owned))
                    .diagnostics().getFirst().code());
            assertEquals(List.of("recovery"), registrar.groups());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            baselines.forEach(value -> value.map().removeRecipe(value.recipe()));
        }
    }

    @Test
    void externallyRestoredBaselineIsDiagnosedWithoutDuplicatingItsCategoryEntry() {
        var original = definition(input(), GTValues.M);
        var baselines = registerBaseline(original);
        var registrar = new RecipeDefinitionRegistrar();
        var baseline = baselines.getFirst();
        try {
            assertTrue(registrar.replaceRecycling("recovery", List.of(), baselines).value().isPresent());
            baseline.map().compileRecipe(baseline.recipe());
            assertEquals("recipe.baseline.changed", registrar.clear().diagnostics().getFirst().code());
            assertEquals(1, baseline.map().getRecipesByCategory().values().stream().flatMap(List::stream)
                    .filter(recipe -> recipe == baseline.recipe()).count());
            baseline.map().removeRecipe(baseline.recipe());
            assertTrue(registrar.clear().value().isPresent());
            baselines.forEach(value -> assertSame(value.recipe(), find(value.map(), value.recipe())));
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            baselines.forEach(value -> value.map().removeRecipe(value.recipe()));
        }
    }

    private static List<NativeRecipeRegistration> registerBaseline(RecyclingRecipeDefinition definition) {
        var prepared = RecyclingRecipeCompiler.prepare(definition).value().orElseThrow();
        RecyclingRecipes.registerRecyclingRecipes(definition.input().stack(),
                definition.materials().stream().map(value -> new MaterialStack(value.material(), value.amount()))
                        .toList(),
                definition.ignoreArcSmelting(), definition.prefix());
        return prepared.stream()
                .map(value -> new NativeRecipeRegistration(value.map(), find(value.map(), value.recipe())))
                .toList();
    }

    private static SourceOrigin origin() {
        return new SourceOrigin("java", "recycling.java", 7, 2, "recovery", "postInit");
    }

    private static ItemStack input() {
        var stack = new ItemStack(Items.PAPER);
        var tag = new NBTTagCompound();
        tag.setInteger("recyclingTest", IDS.incrementAndGet());
        stack.setTagCompound(tag);
        return stack;
    }

    private static Recipe find(RecipeMap<?> map, Recipe recipe) {
        var items = recipe.getInputs().stream().map(input -> {
            var stack = input.getInputStacks()[0].copy();
            stack.setCount(input.getAmount());
            return stack;
        }).toList();
        return map.findRecipe(Long.MAX_VALUE, items,
                recipe.getFluidInputs().stream().map(input -> input.getInputFluidStack()).toList());
    }
}

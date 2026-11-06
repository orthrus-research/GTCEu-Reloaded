package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.CapabilityPolicy;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.NbtPolicy;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDefinitionRegistrarTest {

    private static final AtomicInteger MAP_IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void replacingAndRemovingAGroupRemovesEveryAlternativeAndKeepsHiddenUnownedRecipes() {
        RecipeMap<?> map = map();
        Recipe hidden = map.recipeBuilder().inputs(new ItemStack(Items.EMERALD))
                .outputs(new ItemStack(Items.COAL)).duration(1).EUt(1).hidden().build().getResult();
        assertTrue(map.compileRecipe(hidden));
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        for (int i = 0; i < 4; i++) {
            var receipt = registrar.replace("metals", List.of(definition(map, "metal", Items.DIAMOND, i + 1)));
            assertEquals(i + 1, receipt.value().orElseThrow().generation());
            for (Item input : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) {
                assertEquals(i + 1, find(map, input).getOutputs().getFirst().getCount());
            }
            assertEquals(1, map.getRecipeList().size());
            assertSame(hidden, find(map, Items.EMERALD));
        }
        assertEquals(List.of("metals"), registrar.groups());
        assertEquals(0, registrar.replace("metals", List.of()).value().orElseThrow().recipes());
        assertNull(find(map, Items.IRON_INGOT));
        assertNull(find(map, Items.GOLD_INGOT));
        assertSame(hidden, find(map, Items.EMERALD));
        assertTrue(registrar.groups().isEmpty());
        assertEquals(List.of(hidden), map.getRecipesByCategory().get(hidden.getRecipeCategory()));
    }

    @Test
    void aConflictInTheSecondMapPublishesNeitherMapAndRetainsPriorOwnership() {
        RecipeMap<?> first = map();
        RecipeMap<?> second = map();
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        assertTrue(
                registrar.replace("metals", List.of(definition(first, "first", Items.DIAMOND, 1))).value().isPresent());
        assertTrue(registrar.replace("other", List.of(definition(second, "other", Items.COAL, 1))).value().isPresent());
        Recipe original = find(first, Items.IRON_INGOT);
        Recipe blocker = find(second, Items.IRON_INGOT);
        long firstRevision = first.getRecipeRevision();
        long secondRevision = second.getRecipeRevision();
        var failed = registrar.replace("metals", List.of(definition(first, "first", Items.DIAMOND, 4),
                definition(second, "second", Items.DIAMOND, 5)));
        assertTrue(failed.value().isEmpty());
        assertEquals("recipe.index.conflict", failed.diagnostics().getFirst().code());
        assertEquals("second", failed.diagnostics().getFirst().origin().declaration());
        assertEquals(firstRevision, first.getRecipeRevision());
        assertEquals(secondRevision, second.getRecipeRevision());
        assertSame(original, find(first, Items.IRON_INGOT));
        assertSame(blocker, find(second, Items.IRON_INGOT));
        assertEquals(List.of("metals", "other"), registrar.groups());
        assertTrue(registrar.replace("metals", List.of()).value().isPresent());
        assertNull(find(first, Items.GOLD_INGOT));
        assertSame(blocker, find(second, Items.GOLD_INGOT));
    }

    @Test
    void aRejectedLaterRecipeDiscardsAllStagedAlternativePaths() {
        RecipeMap<?> map = map();
        Recipe existing = map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.COAL)).duration(1).EUt(1).build().getResult();
        assertTrue(map.compileRecipe(existing));
        Recipe alternatives = map.recipeBuilder().inputs(new GTRecipeItemInput(new ItemStack[] {
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT) }, 1))
                .outputs(new ItemStack(Items.DIAMOND)).duration(1).EUt(1).build().getResult();
        Recipe conflict = map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.DIAMOND)).duration(1).EUt(1).build().getResult();
        long revision = map.getRecipeRevision();
        assertNull(map.prepareOwnedChange(List.of(), List.of(alternatives, conflict)));
        assertEquals(revision, map.getRecipeRevision());
        assertNull(find(map, Items.IRON_INGOT));
        assertSame(existing, find(map, Items.GOLD_INGOT));
        assertEquals(List.of(existing), map.getRecipesByCategory().get(existing.getRecipeCategory()));
    }

    @Test
    void semanticFailureAndDuplicateDeclarationIdsLeaveTheOriginalGroupUntouched() {
        RecipeMap<?> map = map();
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        var original = definition(map, "metal", Items.DIAMOND, 1);
        assertTrue(registrar.replace("metals", List.of(original)).value().isPresent());
        Recipe recipe = find(map, Items.IRON_INGOT);
        long revision = map.getRecipeRevision();
        assertTrue(registrar.replace("metals", List.of(original, original)).value().isEmpty());
        var invalid = new RecipeDefinition(original.origin(), original.recipeMap(), original.inputs(),
                original.outputs(), 0, 8, false);
        assertTrue(registrar.replace("metals", List.of(invalid)).value().isEmpty());
        assertEquals(revision, map.getRecipeRevision());
        assertSame(recipe, find(map, Items.GOLD_INGOT));
    }

    @Test
    void clearingAllGroupsStagesEveryMapBeforeRemovingAnyGroup() {
        RecipeMap<?> first = map();
        RecipeMap<?> second = map();
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        assertTrue(registrar.replace("first", List.of(definition(first, "one", Items.DIAMOND, 1))).value().isPresent());
        assertTrue(registrar.replace("second", List.of(definition(second, "two", Items.COAL, 1))).value().isPresent());
        Recipe original = find(first, Items.IRON_INGOT);
        Recipe stolen = find(second, Items.IRON_INGOT);
        second.removeAllRecipes();
        long revision = first.getRecipeRevision();
        var failed = registrar.clear();
        assertTrue(failed.value().isEmpty());
        assertEquals("recipe.ownership.changed", failed.diagnostics().getFirst().code());
        assertEquals(revision, first.getRecipeRevision());
        assertSame(original, find(first, Items.IRON_INGOT));
        assertSame(original, find(first, Items.GOLD_INGOT));
        assertEquals(List.of("first", "second"), registrar.groups());
        assertTrue(second.compileRecipe(stolen));
        assertEquals(2, registrar.clear().value().orElseThrow());
        assertTrue(registrar.groups().isEmpty());
        for (RecipeMap<?> map : List.of(first, second)) {
            assertNull(find(map, Items.IRON_INGOT));
            assertNull(find(map, Items.GOLD_INGOT));
            assertTrue(map.getRecipesByCategory().isEmpty());
        }
        assertEquals(0, registrar.clear().value().orElseThrow());
    }

    @Test
    void externalMutationIsDiagnosedAndOffThreadWritesAreRejected() throws InterruptedException {
        RecipeMap<?> map = map();
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        assertTrue(
                registrar.replace("metals", List.of(definition(map, "metal", Items.DIAMOND, 1))).value().isPresent());
        assertTrue(map.removeRecipe(find(map, Items.IRON_INGOT)));
        var failed = registrar.replace("metals", List.of());
        assertEquals("recipe.ownership.changed", failed.diagnostics().getFirst().code());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                registrar.replace("metals", List.of());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    private static RecipeDefinition definition(RecipeMap<?> map, String identity, Item output, int amount) {
        var input = new ItemIngredient(List.of(ItemValue.of(new ItemStack(Items.IRON_INGOT)),
                ItemValue.of(new ItemStack(Items.GOLD_INGOT))), 1, true, NbtPolicy.exact(null), CapabilityPolicy.MATCH);
        return new RecipeDefinition(new SourceOrigin("java", "test", 1, 1, identity, "recipes"), map.unlocalizedName,
                List.of(input), List.of(ItemValue.of(new ItemStack(output, amount))), 40, 8, false);
    }

    @Test
    void lifecycleThreadHandoffPreservesGroupsAndRevokesTheStartupWriter() throws InterruptedException {
        AtomicReference<Thread> authorized = new AtomicReference<>(Thread.currentThread());
        var registrar = new RecipeDefinitionRegistrar(() -> Thread.currentThread() == authorized.get());
        RecipeMap<?> map = map();
        assertTrue(registrar.replace("handoff", List.of(definition(map, "entry", Items.DIAMOND, 1)))
                .value().isPresent());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread server = Thread.ofPlatform().unstarted(() -> {
            try {
                assertEquals(1, registrar.clear().value().orElseThrow());
                assertTrue(registrar.replace("handoff", List.of(definition(map, "entry", Items.COAL, 1)))
                        .value().isPresent());
                assertEquals(1, registrar.clear().value().orElseThrow());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        authorized.set(server);
        assertThrows(IllegalStateException.class, registrar::clear);
        server.start();
        server.join(5000);
        assertFalse(server.isAlive());
        assertNull(failure.get());
        assertTrue(map.getRecipeList().isEmpty());
    }

    private static RecipeMap<?> map() {
        return new RecipeMapBuilder<>("owned_test_" + MAP_IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    private static Recipe find(RecipeMap<?> map, Item item) {
        return map.findRecipe(8, List.of(new ItemStack(item)), List.of());
    }
}

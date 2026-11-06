package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRegistrationStateTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void imperativeRegistrationRemovalAndClearMaintainTheDeclaredIndex() {
        var map = new IndexedMap();
        var registration = map.recipeBuilder().inputs(new ItemStack(Items.COAL)).outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        assertEquals(List.of(registration.recipe()), map.index.value());
        assertThrows(UnsupportedOperationException.class, () -> map.index.value().clear());
        var before = map.index.value();
        var conflict = map.recipeBuilder().inputs(new ItemStack(Items.COAL)).outputs(new ItemStack(Items.EMERALD))
                .duration(20).EUt(8).buildAndRegisterWithReceipt();
        assertTrue(conflict.isEmpty());
        assertSame(before, map.index.value());
        assertTrue(map.removeRecipe(registration.recipe()));
        assertTrue(map.index.value().isEmpty());
        assertFalse(map.removeRecipe(registration.recipe()));
        map.compileRecipe(registration.recipe());
        GTRecipeHandler.removeAllRecipes(map);
        assertTrue(map.index.value().isEmpty());
        assertTrue(map.getRecipeList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void newMapSupportsGroovyReplacementDisableAndReenableWithoutCompilerRegistration(boolean checked) {
        var map = new IndexedMap();
        var registrar = new RecipeDefinitionRegistrar();
        var binding = new Binding();
        binding.setVariable("registrar", registrar);
        binding.setVariable("declaration", definition(map, Items.COAL));
        var shell = new GroovyShell(getClass().getClassLoader(), binding);
        shell.evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.recipes.RecipeDefinitionRegistrar
                import gregtech.api.recipes.definition.RecipeDefinition
                %s
                class Author {
                    static void replace(RecipeDefinitionRegistrar registrar, RecipeDefinition declaration) {
                        assert registrar.replace('owned', List.of(declaration)).value().isPresent()
                    }
                }
                Author.replace(registrar, declaration)
                """.formatted(checked ? "@CompileStatic" : ""));
        assertEquals(1, map.index.value().size());
        var old = map.index.value().getFirst();
        binding.setVariable("declaration", definition(map, Items.IRON_INGOT));
        shell.evaluate("Author.replace(registrar, declaration)");
        assertEquals(1, map.index.value().size());
        assertNotSame(old, map.index.value().getFirst());
        assertFalse(map.getRecipeList().stream().anyMatch(recipe -> recipe == old));
        assertTrue(registrar.replace("owned", List.of()).value().isPresent());
        assertTrue(map.index.value().isEmpty());
        shell.evaluate("Author.replace(registrar, declaration)");
        assertEquals(1, map.index.value().size());
        assertTrue(registrar.clear().value().isPresent());
        assertTrue(map.index.value().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void failedSecondaryPreparationPreservesBothMapsAndTheirPriorIndexes(int rejection) {
        var first = new IndexedMap();
        var second = new IndexedMap();
        var registrar = new RecipeDefinitionRegistrar();
        assertTrue(registrar.replace("pair", List.of(definition(first, Items.COAL), definition(second, Items.COAL)))
                .value().isPresent());
        var firstBefore = first.index.value();
        var secondBefore = second.index.value();
        long firstRevision = first.getRecipeRevision();
        long secondRevision = second.getRecipeRevision();
        second.rejection = rejection;
        var failure = registrar.replace("pair", List.of(definition(first, Items.IRON_INGOT),
                definition(second, Items.IRON_INGOT)));
        assertTrue(failure.value().isEmpty());
        assertEquals(definition(second, Items.IRON_INGOT).origin(), failure.diagnostics().getFirst().origin());
        assertSame(firstBefore, first.index.value());
        assertSame(secondBefore, second.index.value());
        assertEquals(firstBefore, List.copyOf(first.getRecipeList()));
        assertEquals(secondBefore, List.copyOf(second.getRecipeList()));
        assertEquals(firstRevision, first.getRecipeRevision());
        assertEquals(secondRevision, second.getRecipeRevision());
        second.rejection = 0;
        assertTrue(registrar.clear().value().isPresent());
    }

    @Test
    void nativePreparationFailurePublishesNeitherPrimaryNorSecondaryIndex() {
        var map = new IndexedMap();
        map.rejection = 1;
        long before = map.getRecipeRevision();
        assertThrows(IllegalStateException.class, () -> map.recipeBuilder().inputs(new ItemStack(Items.COAL))
                .outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8).buildAndRegister());
        assertTrue(map.index.value().isEmpty());
        assertTrue(map.getRecipeList().isEmpty());
        assertEquals(before, map.getRecipeRevision());
    }

    @Test
    void explicitIndexChangesInvalidatePreparedGroupsAndClearAlsoClearsDetachedEntries() {
        var map = new IndexedMap();
        var recipe = RecipeDefinitionCompiler.compile(definition(map, Items.COAL)).value().orElseThrow();
        var prepared = map.prepareOwnedChange(List.of(), List.of(recipe));
        assertTrue(RecipeRegistrationBatch.isCurrent(List.of(prepared)));
        map.index.update(previous -> List.of(recipe));
        assertFalse(RecipeRegistrationBatch.isCurrent(List.of(prepared)));
        long before = map.getRecipeRevision();
        GTRecipeHandler.removeAllRecipes(map);
        assertTrue(map.index.value().isEmpty());
        assertTrue(map.getRecipeRevision() > before);
    }

    private static RecipeDefinition definition(RecipeMap<?> map, Item input) {
        var source = new SourceOrigin("java", "NewMap.java", 1, 1, map.unlocalizedName, "postInit");
        return new RecipeDefinition(source, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(input))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 20, 8, false);
    }

    private static final class IndexedMap extends RecipeMap<SimpleRecipeBuilder> {

        private int rejection;
        private final RecipeRegistrationState<List<Recipe>> index = registrationState(List.of(), (current, change) -> {
            if (rejection == 1) throw new IllegalStateException("Fixture rejected preparation");
            if (rejection == 2) throw new IllegalArgumentException("Fixture rejected the declaration");
            if (rejection == 3) return null;
            if (change.clear()) return List.of();
            var next = new ArrayList<>(current);
            next.removeIf(recipe -> change.removed().stream().anyMatch(removed -> removed == recipe));
            next.addAll(change.added());
            return List.copyOf(next);
        });

        private IndexedMap() {
            super("unlisted_map_" + IDS.incrementAndGet(), new SimpleRecipeBuilder(),
                    map -> new RecipeMapUI<>(map, true, true, true, true, false), 1, 1, 0, 0);
        }
    }
}

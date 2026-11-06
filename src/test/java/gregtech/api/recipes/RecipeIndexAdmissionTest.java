package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeIndexAdmissionTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void prefixAndDescendantAreReachableInEitherRegistrationOrder() {
        for (boolean shortFirst : List.of(false, true)) {
            var map = map();
            var longer = recipe(map, Items.IRON_INGOT, Items.COAL);
            var shorter = recipe(map, Items.IRON_INGOT);
            assertTrue(map.compileRecipe(shortFirst ? shorter : longer));
            assertTrue(map.compileRecipe(shortFirst ? longer : shorter));
            assertEquals(2, map.getRecipeList().size());
            assertEquals(2, map.getRecipesByCategory().get(shorter.getRecipeCategory()).size());
            assertSame(shorter, find(map, Items.IRON_INGOT));
            assertSame(longer, find(map, Items.IRON_INGOT, Items.COAL));
            assertSame(longer, find(map, Items.COAL, Items.IRON_INGOT));
            assertFalse(map.canReuseRecipe(shorter));
            assertTrue(map.canReuseRecipe(longer));
            assertEquals(Set.of(shorter, longer), map.findRecipeCollisions(
                    List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL)), List.of()));
        }
    }

    @Test
    void removingEitherRecipePreservesTheOtherAndAllowsReplacement() {
        for (boolean removeShort : List.of(false, true)) {
            var map = map();
            var shorter = recipe(map, Items.IRON_INGOT);
            var longer = recipe(map, Items.IRON_INGOT, Items.COAL);
            assertTrue(map.compileRecipe(shorter));
            assertTrue(map.compileRecipe(longer));
            var removed = removeShort ? shorter : longer;
            assertTrue(map.removeRecipe(removed));
            assertFalse(map.removeRecipe(removed));
            assertSame(removeShort ? longer : shorter, find(map, Items.IRON_INGOT, Items.COAL));
            if (removeShort) assertNull(find(map, Items.IRON_INGOT));
            assertTrue(map.compileRecipe(removed));
            assertSame(shorter, find(map, Items.IRON_INGOT));
            assertSame(longer, find(map, Items.IRON_INGOT, Items.COAL));
            map.removeAllRecipes();
            assertTrue(map.getRecipeList().isEmpty());
            assertNull(find(map, Items.IRON_INGOT, Items.COAL));
        }
    }

    @Test
    void duplicateTerminalRejectsWithoutReplacingEitherRecipe() {
        var map = map();
        var shorter = recipe(map, Items.IRON_INGOT);
        var longer = recipe(map, Items.IRON_INGOT, Items.COAL);
        assertTrue(map.compileRecipe(longer));
        assertTrue(map.compileRecipe(shorter));
        long revision = map.getRecipeRevision();
        assertFalse(map.compileRecipe(recipe(map, Items.IRON_INGOT)));
        assertFalse(map.compileRecipe(recipe(map, Items.IRON_INGOT, Items.COAL)));
        assertEquals(revision, map.getRecipeRevision());
        assertSame(shorter, find(map, Items.IRON_INGOT));
        assertSame(longer, find(map, Items.IRON_INGOT, Items.COAL));
    }

    @Test
    void ownedPreparationCopiesAndRemovesTerminalsWithoutChangingThePublishedTree() {
        var map = map();
        var shorter = recipe(map, Items.IRON_INGOT);
        var longer = recipe(map, Items.IRON_INGOT, Items.COAL);
        assertTrue(map.compileRecipe(longer));
        var addition = map.prepareOwnedChange(List.of(), List.of(shorter));
        assertNotNull(addition);
        assertNull(find(map, Items.IRON_INGOT));
        map.publishOwnedChange(addition);
        assertSame(shorter, find(map, Items.IRON_INGOT));
        var removal = map.prepareOwnedChange(List.of(shorter), List.of());
        assertNotNull(removal);
        assertSame(shorter, find(map, Items.IRON_INGOT));
        map.publishOwnedChange(removal);
        assertNull(find(map, Items.IRON_INGOT));
        assertSame(longer, find(map, Items.IRON_INGOT, Items.COAL));
    }

    @Test
    void failedOwnedDuplicatePreservesTheGroupAndBothPrefixRecipes() {
        var map = map();
        var shorter = recipe(map, Items.IRON_INGOT);
        var longer = recipe(map, Items.IRON_INGOT, Items.COAL);
        var owned = recipe(map, Items.EMERALD);
        assertTrue(map.compileRecipe(shorter));
        assertTrue(map.compileRecipe(longer));
        assertTrue(map.compileRecipe(owned));
        assertNull(map.prepareOwnedChange(List.of(owned), List.of(recipe(map, Items.IRON_INGOT))));
        assertSame(owned, find(map, Items.EMERALD));
        assertSame(shorter, find(map, Items.IRON_INGOT));
        assertSame(longer, find(map, Items.IRON_INGOT, Items.COAL));
    }

    @Test
    void nativeQuantityVoltageAndEligibilityChecksCanFallBackToShorterRecipe() {
        var map = map();
        var shorter = recipe(map, Items.IRON_INGOT);
        var longer = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL, 2))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(20).EUt(32).build().getResult();
        assertTrue(map.compileRecipe(shorter));
        assertTrue(map.compileRecipe(longer));
        var items = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COAL, 2));
        assertSame(shorter, map.findRecipe(8, items, List.of()));
        assertSame(longer, map.findRecipe(32, items, List.of()));
        assertSame(shorter, map.findRecipe(32, List.of(new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.COAL)), List.of()));
        assertSame(shorter, map.findWithEligibility(items, List.of(),
                r -> r.matches(false, items, List.of()), r -> r != longer).getRecipe());
    }

    @Test
    void fluidPrefixesRespectAmountsAndRemoval() {
        var map = map();
        var water = FluidRegistry.WATER;
        var lava = FluidRegistry.LAVA;
        var shorter = map.recipeBuilder().fluidInputs(new FluidStack(water, 500))
                .outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8).build().getResult();
        var longer = map.recipeBuilder().fluidInputs(new FluidStack(water, 1000),
                new FluidStack(lava, 500))
                .outputs(new ItemStack(Items.EMERALD)).duration(20).EUt(8).build().getResult();
        assertTrue(map.compileRecipe(shorter));
        assertTrue(map.compileRecipe(longer));
        assertSame(shorter, map.findRecipe(8, List.of(), List.of(
                new FluidStack(water, 500),
                new FluidStack(lava, 500))));
        var full = List.of(new FluidStack(lava, 500),
                new FluidStack(water, 1000));
        assertSame(longer, map.findRecipe(8, List.of(), full));
        assertTrue(map.removeRecipe(shorter));
        assertSame(longer, map.findRecipe(8, List.of(), full));
    }

    @Test
    void removingAlternativeInputsClearsEveryPathIncludingHiddenTerminals() {
        var map = map();
        var alternatives = new GTRecipeItemInput(new ItemStack[] {
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT) }, 1);
        var shorter = map.recipeBuilder().inputs(alternatives).outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(8).hidden().build().getResult();
        var longer = map.recipeBuilder().inputs(alternatives).inputs(new ItemStack(Items.COAL))
                .outputs(new ItemStack(Items.EMERALD)).duration(20).EUt(8).build().getResult();
        assertTrue(map.compileRecipe(shorter));
        assertTrue(map.compileRecipe(longer));
        assertEquals(1, map.getRecipeList().size());
        assertTrue(map.removeRecipe(shorter));
        for (Item input : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) {
            assertNull(find(map, input));
            assertSame(longer, find(map, input, Items.COAL));
        }
        assertTrue(map.removeRecipe(longer));
        for (Item input : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) assertNull(find(map, input, Items.COAL));
        assertTrue(map.getRecipeList().isEmpty());
    }

    @Test
    void registrationConflictQueryExcludesCraftablePrefixesAndDescendants() {
        var map = map();
        var shorter = recipe(map, Items.IRON_INGOT);
        var longer = recipe(map, Items.IRON_INGOT, Items.COAL);
        assertTrue(map.compileRecipe(shorter));
        assertTrue(map.compileRecipe(longer));
        long revision = map.getRecipeRevision();
        var shortConflicts = map.registrationConflicts(recipe(map, Items.IRON_INGOT));
        assertEquals(1, shortConflicts.size());
        assertSame(shorter, shortConflicts.getFirst());
        var longConflicts = map.registrationConflicts(recipe(map, Items.IRON_INGOT, Items.COAL));
        assertEquals(1, longConflicts.size());
        assertSame(longer, longConflicts.getFirst());
        assertTrue(map.registrationConflicts(recipe(map, Items.IRON_INGOT, Items.COAL, Items.STICK)).isEmpty());
        assertTrue(map.registrationConflicts(shorter).isEmpty());
        assertEquals(revision, map.getRecipeRevision());
        assertSame(shorter, find(map, Items.IRON_INGOT));
        assertSame(longer, find(map, Items.IRON_INGOT, Items.COAL));
    }

    @Test
    void registrationConflictQueryPreservesOverlappingAlternativesAndSeparatesFluidRequirements() {
        var map = map();
        var iron = recipe(map, Items.IRON_INGOT);
        var gold = recipe(map, Items.GOLD_INGOT);
        assertTrue(map.compileRecipe(iron));
        assertTrue(map.compileRecipe(gold));
        var alternatives = map.recipeBuilder().inputs(new GTRecipeItemInput(new ItemStack[] {
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT) }, 8))
                .outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8).build().getResult();
        assertTrue(map.registrationConflicts(alternatives).isEmpty(),
                "Overlapping alternatives are not equal native registration keys");
        assertTrue(map.compileRecipe(alternatives));
        var alternativesCopy = map.recipeBuilder().inputs(new GTRecipeItemInput(new ItemStack[] {
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT) }, 1))
                .outputs(new ItemStack(Items.EMERALD)).duration(10).EUt(4).build().getResult();
        var found = map.registrationConflicts(alternativesCopy);
        assertEquals(1, found.size());
        assertSame(alternatives, found.getFirst());
        var wet = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 1000))
                .outputs(new ItemStack(Items.EMERALD)).duration(20).EUt(8).build().getResult();
        assertTrue(map.registrationConflicts(wet).isEmpty());
        assertTrue(map.compileRecipe(wet));
        assertEquals(1, map.registrationConflicts(recipe(map, Items.IRON_INGOT)).size());
        var wetCopy = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 500))
                .outputs(new ItemStack(Items.DIAMOND)).duration(10).EUt(4).build().getResult();
        assertSame(wet, map.registrationConflicts(wetCopy).getFirst());
    }

    private static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("index_admission_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(3).itemOutputs(1).fluidInputs(2).build();
    }

    private static Recipe recipe(RecipeMap<SimpleRecipeBuilder> map, Item... inputs) {
        return map.recipeBuilder().inputs(Arrays.stream(inputs).map(ItemStack::new).toArray(ItemStack[]::new))
                .outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8).build().getResult();
    }

    private static Recipe find(RecipeMap<?> map, Item... inputs) {
        return map.findRecipe(8, Arrays.stream(inputs).map(ItemStack::new).toList(), List.of());
    }
}

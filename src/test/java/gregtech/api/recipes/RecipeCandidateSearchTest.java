package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.machines.RecipeMapFluidCanner;
import gregtech.api.recipes.machines.RecipeMapFormingPress;
import gregtech.api.recipes.machines.RecipeMapFurnace;
import gregtech.api.recipes.machines.RecipeMapScanner;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.recipes.ui.RecipeMapUIFunction;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeCandidateSearchTest {

    private static int nextId;
    private static final RecipeMapUIFunction UI = map -> new RecipeMapUI<>(map, false, false, false, false, false);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void fallbackPreservesTrieOrderForOverlappingOreAndExactInputs() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        String ore = "candidateTestOre" + nextId++;
        OreDictionary.registerOre(ore, new ItemStack(Items.IRON_INGOT));
        Recipe exact = add(map, Items.IRON_INGOT);
        Recipe oreRecipe = register(map, map.recipeBuilder().input(ore));
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        FluidTankList fluids = new FluidTankList(false);
        Recipe first = map.findRecipe(32, input, fluids);
        assertTrue(first == exact || first == oreRecipe);
        Recipe expected = first == exact ? oreRecipe : exact;

        for (int i = 0; i < 10; i++) {
            RecipeSearchResult result = map.findRecipeWithEligibility(32, input, fluids, recipe -> recipe != first);
            assertSame(expected, result.getRecipe());
            assertTrue(result.hasInputMatch());
            assertEquals(8, input.getStackInSlot(0).getCount());
        }
        Map<Recipe, Integer> visits = new IdentityHashMap<>();
        RecipeSearchResult rejected = map.findWithEligibility(Collections.singletonList(input.getStackInSlot(0)),
                Collections.emptyList(), recipe -> true, recipe -> {
                    visits.merge(recipe, 1, Integer::sum);
                    return false;
                });
        assertNull(rejected.getRecipe());
        assertTrue(rejected.hasInputMatch());
        assertSame(first, rejected.getRecipeForCache());
        assertEquals(2, visits.size());
        assertTrue(visits.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void noInputMatchIsDifferentFromAllCandidatesBeingTemporarilyRejected() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe registered = add(map, Items.IRON_INGOT);
        ItemStackHandler input = new ItemStackHandler(1);
        RecipeSearchResult empty = map.findRecipeWithEligibility(32, input, new FluidTankList(false), recipe -> false);
        assertFalse(empty.hasInputMatch());
        assertNull(empty.getRecipeForCache());
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        RecipeSearchResult blocked = map.findRecipeWithEligibility(32, input, new FluidTankList(false),
                recipe -> false);
        assertTrue(blocked.hasInputMatch());
        assertNull(blocked.getRecipe());
        assertSame(registered, blocked.getRecipeForCache());
    }

    @Test
    void externalLegacyLookupRemainsAuthoritativeUntilExplicitOptIn() {
        LegacyMap map = new LegacyMap();
        Recipe first = add(map, Items.IRON_INGOT);
        Recipe alternative = add(map, Items.GOLD_INGOT);
        map.returned = first;
        ItemStackHandler input = bothInputs();
        RecipeSearchResult result = map.findRecipeWithEligibility(32, input, new FluidTankList(false),
                recipe -> recipe == alternative);
        assertEquals(1, map.legacyCalls);
        assertNull(result.getRecipe());
        assertSame(first, result.getRecipeForCache());
        map.fallback = true;
        result = map.findRecipeWithEligibility(32, input, new FluidTankList(false), recipe -> recipe == alternative);
        assertSame(alternative, result.getRecipe());
        assertEquals(2, map.legacyCalls);
    }

    @Test
    void builtInDynamicMapsKeepTheirLegacyFirstChoiceAndSupportRegisteredAlternatives() {
        for (RecipeMap<SimpleRecipeBuilder> map : Arrays.asList(
                new RecipeMapFurnace(name(), new SimpleRecipeBuilder(), UI),
                new RecipeMapFormingPress(name(), new SimpleRecipeBuilder(), UI),
                new RecipeMapFluidCanner(name(), new SimpleRecipeBuilder(), UI),
                new RecipeMapScanner(name(), new SimpleRecipeBuilder(), UI))) {
            Recipe iron = add(map, Items.IRON_INGOT);
            Recipe gold = add(map, Items.GOLD_INGOT);
            ItemStackHandler input = bothInputs();
            FluidTankList fluids = new FluidTankList(false);
            Recipe legacy = map.findRecipe(32, input, fluids);
            assertSame(legacy, map.findRecipeWithEligibility(32, input, fluids, recipe -> true).getRecipe());
            Recipe alternative = legacy == iron ? gold : iron;
            assertSame(alternative, map.findRecipeWithEligibility(32, input, fluids,
                    recipe -> recipe != legacy).getRecipe());
        }
    }

    @Test
    void furnaceDynamicGenerationStillRunsWhenNoRegisteredInputsMatch() {
        RecipeMapFurnace map = new RecipeMapFurnace(name(), new SimpleRecipeBuilder(), UI);
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.PORKCHOP, 2));
        RecipeSearchResult result = map.findRecipeWithEligibility(32, input, new FluidTankList(false), recipe -> true);
        assertNotNull(result.getRecipe());
        assertEquals(Items.COOKED_PORKCHOP, result.getRecipe().getOutputs().get(0).getItem());
        assertEquals(2, input.getStackInSlot(0).getCount());
    }

    @Test
    void listInputEligibilityPreservesExternalLegacyLookupAndDynamicFurnaceGeneration() {
        LegacyMap map = new LegacyMap();
        Recipe first = add(map, Items.IRON_INGOT);
        Recipe alternative = add(map, Items.GOLD_INGOT);
        map.returned = first;
        List<ItemStack> inputs = Arrays.asList(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
        RecipeSearchResult blocked = map.findRecipeWithEligibility(32, inputs, Collections.emptyList(),
                recipe -> recipe == alternative);
        assertNull(blocked.getRecipe());
        assertSame(first, blocked.getRecipeForCache());
        assertEquals(1, map.legacyCalls);
        map.fallback = true;
        assertSame(alternative, map.findRecipeWithEligibility(32, inputs, Collections.emptyList(),
                recipe -> recipe == alternative).getRecipe());
        assertEquals(2, map.legacyCalls);
        RecipeMapFurnace furnace = new RecipeMapFurnace(name(), new SimpleRecipeBuilder(), UI);
        Recipe generated = furnace
                .findRecipeWithEligibility(32, Collections.singletonList(new ItemStack(Items.PORKCHOP)),
                        Collections.emptyList(), recipe -> true)
                .getRecipe();
        assertNotNull(generated);
        assertEquals(Items.COOKED_PORKCHOP, generated.getOutputs().get(0).getItem());
    }

    private static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>(name(), new SimpleRecipeBuilder()).itemInputs(2).itemOutputs(1).build();
    }

    private static String name() {
        return "candidate_map_" + nextId++;
    }

    private static Recipe add(RecipeMap<SimpleRecipeBuilder> map, Item input) {
        return register(map, map.recipeBuilder().inputs(new ItemStack(input)));
    }

    private static Recipe register(RecipeMap<SimpleRecipeBuilder> map, SimpleRecipeBuilder builder) {
        Recipe recipe = builder.outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).build().getResult();
        assertTrue(map.compileRecipe(recipe));
        return recipe;
    }

    private static ItemStackHandler bothInputs() {
        ItemStackHandler input = new ItemStackHandler(2);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        input.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 8));
        return input;
    }

    private static final class LegacyMap extends RecipeMap<SimpleRecipeBuilder> {

        Recipe returned;
        boolean fallback;
        int legacyCalls;

        LegacyMap() {
            super(name(), new SimpleRecipeBuilder(), UI, 2, 1, 0, 0);
        }

        @Override
        public Recipe findRecipe(long voltage, IItemHandlerModifiable inputs, IMultipleTankHandler fluids) {
            legacyCalls++;
            return returned;
        }

        @Override
        public Recipe findRecipe(long voltage, List<ItemStack> inputs, List<FluidStack> fluids) {
            legacyCalls++;
            return returned;
        }

        @Override
        protected boolean supportsRecipeCandidateFallback() {
            return fallback;
        }
    }
}

package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeScriptingTest {

    private static int mapId;

    private Recorder scripts;
    private RecipeMap<SimpleRecipeBuilder> map;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void install() {
        scripts = new Recorder();
        RecipeScripting.install(scripts);
        map = new RecipeMapBuilder<>("recipe_scripting_" + mapId++, new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    @AfterEach
    void uninstall() {
        RecipeScripting.install(new RecipeScripting.Frontend() {});
    }

    @Test
    void mapsAnnounceThemselvesOnCreation() {
        assertEquals(List.of(map), scripts.maps);
    }

    @Test
    void recipesBuiltWhileAScriptRunsAreScriptRecipesTheFrontendTracks() {
        scripts.running = true;
        Recipe recipe = register(Items.IRON_INGOT, 8);
        assertTrue(recipe.isGroovyRecipe());
        assertEquals(List.of(recipe), scripts.added);
        assertTrue(map.removeRecipe(recipe));
        assertEquals(List.of(recipe), scripts.removed);
    }

    @Test
    void recipesBuiltOutsideScriptsReachNoFrontend() {
        Recipe recipe = register(Items.IRON_INGOT, 8);
        assertFalse(recipe.isGroovyRecipe());
        assertTrue(map.removeRecipe(recipe));
        assertTrue(scripts.added.isEmpty());
        assertTrue(scripts.removed.isEmpty());
    }

    @Test
    void removingEveryRecipeDuringAScriptReportsEachOne() {
        Recipe iron = register(Items.IRON_INGOT, 8);
        Recipe gold = register(Items.GOLD_INGOT, 8);
        scripts.running = true;
        GTRecipeHandler.removeAllRecipes(map);
        assertEquals(2, scripts.removed.size());
        assertTrue(scripts.removed.contains(iron) && scripts.removed.contains(gold));
    }

    @Test
    void anInvalidScriptRecipeIsReportedAndSkipped() {
        scripts.running = true;
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))
                .outputs(new ItemStack(Items.DIAMOND)).duration(0).EUt(8).buildAndRegister();
        assertEquals(List.of("Error adding GregTech " + map.unlocalizedName + " recipe"), scripts.titles);
        assertEquals(List.of("Duration must not be less or equal to 0",
                "Must have at most 1 item input, but found 2"), scripts.problems);
        assertTrue(map.getRecipeList().isEmpty());
        assertTrue(scripts.added.isEmpty());
    }

    @Test
    void aConflictingScriptRecipeIsReported() {
        scripts.running = true;
        Recipe first = register(Items.IRON_INGOT, 8);
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(8).buildAndRegister();
        assertEquals(List.of(first), scripts.added);
        assertEquals(1, scripts.conflicts.size());
        assertNotSame(first, scripts.conflicts.getFirst());
    }

    private Recipe register(Item input, long eut) {
        return map.recipeBuilder().inputs(new ItemStack(input)).outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(eut).buildAndRegisterWithReceipt().orElseThrow().recipe();
    }

    private static final class Recorder implements RecipeScripting.Frontend {

        boolean running;
        final List<RecipeMap<?>> maps = new ArrayList<>();
        final List<Recipe> added = new ArrayList<>();
        final List<Recipe> removed = new ArrayList<>();
        final List<Recipe> conflicts = new ArrayList<>();
        final List<String> titles = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean reportInvalid(@NotNull String title, @NotNull List<String> problems) {
            if (problems.isEmpty()) return false;
            titles.add(title);
            this.problems.addAll(problems);
            return true;
        }

        @Override
        public void reportConflict(@NotNull RecipeMap<?> map, @NotNull Recipe recipe, @NotNull String details) {
            conflicts.add(recipe);
        }

        @Override
        public void mapCreated(@NotNull RecipeMap<?> map, @NotNull Class<?> builderType) {
            assertSame(SimpleRecipeBuilder.class, builderType);
            maps.add(map);
        }

        @Override
        public void scriptRecipeAdded(@NotNull RecipeMap<?> map, @NotNull Recipe recipe) {
            added.add(recipe);
        }

        @Override
        public void recipeRemoved(@NotNull RecipeMap<?> map, @NotNull Recipe recipe) {
            removed.add(recipe);
        }
    }
}

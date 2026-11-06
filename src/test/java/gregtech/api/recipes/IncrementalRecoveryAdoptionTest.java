package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.*;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalRecoveryAdoptionTest {

    private static final AtomicInteger IDS = new AtomicInteger(24000);
    private static final SourceOrigin SOURCE = new SourceOrigin("java", "IncrementalRecoveryAdoptionTest", 1, 1,
            "incremental", "postInit");
    private static RecyclingUnificationFixture unification;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.perform();
        unification = new RecyclingUnificationFixture();
    }

    @AfterAll
    static void close() throws Exception {
        unification.close();
    }

    @Test
    void recoveryGroupCanAdoptLaterRecipesAndRestoreEveryOriginal() {
        exercise(false);
    }

    @Test
    void craftingGroupCanAdoptLaterRecipesWithoutPartialCraftingMutation() {
        exercise(true);
    }

    private static void exercise(boolean crafting) {
        var registrar = new RecipeDefinitionRegistrar();
        var other = new RecipeDefinitionRegistrar();
        var first = input();
        var second = input();
        var name = new ResourceLocation("incremental_recovery_test", "recipe_" + IDS.incrementAndGet());
        var definition = new ShapelessCraftingDefinition(SOURCE, name, "", ItemValue.of(new ItemStack(Items.DIAMOND)),
                List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
        List<CraftingMutation> declarations = List.of(definition);
        var originalFirst = seed(first);
        NativeRecipeRegistration originalSecond = null;
        try {
            assertTrue(replace(registrar, crafting, declarations, List.of(route(first, GTValues.M)),
                    List.of(policy(first)),
                    List.of(originalFirst)).value().isPresent());
            originalSecond = seed(second);
            var previous = find(first);
            var generated = registrar.generatedRecovery("incremental");
            var initialGenerated = previous;
            assertTrue(generated.stream().anyMatch(value -> value.recipe() == initialGenerated));
            assertTrue(generated.stream().noneMatch(value -> value.recipe() == originalFirst.recipe()));
            assertThrows(UnsupportedOperationException.class, generated::clear);
            assertTrue(registrar.generatedRecovery("absent").isEmpty());
            assertTrue(other.generatedRecovery("incremental").isEmpty());
            var previousCrafting = ForgeRegistries.RECIPES.getValue(name);
            var invalid = replace(registrar, crafting, List.of(new DisabledCraftingDefinition(SOURCE, name)),
                    List.of(route(first, GTValues.M), route(second, Long.MAX_VALUE)),
                    List.of(policy(first), policy(second)), List.of(originalSecond));
            assertTrue(invalid.value().isEmpty());
            assertSame(previous, find(first));
            assertSame(originalSecond.recipe(), find(second));
            assertSame(previousCrafting, ForgeRegistries.RECIPES.getValue(name));
            assertEquals(generated, registrar.generatedRecovery("incremental"));

            var routes = List.of(route(first, 2 * GTValues.M), route(second, 3 * GTValues.M));
            var policies = List.of(policy(first), policy(second));
            var accepted = replace(registrar, crafting, declarations, routes, policies, List.of(originalSecond));
            assertTrue(accepted.value().isPresent(), () -> accepted.diagnostics().toString());
            assertEquals(2, find(first).getOutputs().getFirst().getCount());
            assertEquals(3, find(second).getOutputs().getFirst().getCount());
            var firstGenerated = previous;
            assertTrue(registrar.generatedRecovery("incremental").stream()
                    .noneMatch(value -> value.recipe() == firstGenerated));
            previous = find(first);
            var previousSecond = find(second);
            assertEquals("recipe.baseline.identity", replace(registrar, crafting, declarations, routes, policies,
                    List.of(originalFirst)).diagnostics().getFirst().code());
            assertEquals("recipe.baseline.identity", replace(registrar, crafting, declarations, routes, policies,
                    List.of(new NativeRecipeRegistration(RecipeMaps.MACERATOR_RECIPES, previous)))
                    .diagnostics().getFirst().code());
            assertEquals("recipe.baseline.identity", other.replaceRecovery("steal", List.of(), List.of(),
                    List.of(new NativeRecipeRegistration(RecipeMaps.MACERATOR_RECIPES, previous)))
                    .diagnostics().getFirst().code());
            assertSame(previous, find(first));
            assertSame(previousSecond, find(second));
            assertTrue(replace(registrar, crafting, List.of(), List.of(), List.of(), List.of()).value().isPresent());
            assertNull(find(first));
            assertNull(find(second));
            assertTrue(registrar.generatedRecovery("incremental").isEmpty());
            if (crafting) assertTrue(ForgeRegistries.RECIPES.getValue(name).isDynamic());
            assertTrue(replace(registrar, crafting, declarations, routes, policies, List.of()).value().isPresent());
            assertTrue(registrar.clear().value().isPresent());
            assertTrue(registrar.generatedRecovery("incremental").isEmpty());
            assertSame(originalFirst.recipe(), find(first));
            assertSame(originalSecond.recipe(), find(second));
            assertFalse(ForgeRegistries.RECIPES.containsKey(name));
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            assertTrue(other.clear().value().isPresent());
            originalFirst.map().removeRecipe(originalFirst.recipe());
            if (originalSecond != null) originalSecond.map().removeRecipe(originalSecond.recipe());
        }
    }

    private static gregtech.api.definition.DefinitionResult<RegistrationReceipt> replace(
                                                                                         RecipeDefinitionRegistrar registrar,
                                                                                         boolean crafting,
                                                                                         List<CraftingMutation> declarations,
                                                                                         List<RecyclingContribution> routes,
                                                                                         List<RecyclingRecoveryDefinition> policies,
                                                                                         List<NativeRecipeRegistration> baselines) {
        return crafting ? registrar.replaceCrafting("incremental", declarations, routes, policies, baselines) :
                registrar.replaceRecovery("incremental", routes, policies, baselines);
    }

    private static ItemStack input() {
        return new ItemStack(Items.PAPER, 1, IDS.incrementAndGet());
    }

    private static NativeRecipeRegistration seed(ItemStack input) {
        return RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(input).outputs(new ItemStack(Items.COAL))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
    }

    private static Recipe find(ItemStack input) {
        return RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of());
    }

    private static RecyclingContribution route(ItemStack output, long amount) {
        return new RecyclingContribution(SOURCE, new ItemAndMetadata(output), 1, Map.of(),
                Map.of(Materials.Iron, amount), RecyclingRoutePolicy.REPLACE);
    }

    private static RecyclingRecoveryDefinition policy(ItemStack input) {
        return new RecyclingRecoveryDefinition(SOURCE, ItemValue.of(input), true, null);
    }
}

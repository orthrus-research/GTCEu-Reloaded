package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
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

import static org.junit.jupiter.api.Assertions.*;

class FixedCraftingRecoveryTest {

    private static final SourceOrigin SOURCE = new SourceOrigin("java", "fixed_recovery", 1, 1, "recipe", "postInit");
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
    void fixedRecoveryAndCraftingDoNotTakeAnotherOwnersComposition() {
        var composition = new RecipeDefinitionRegistrar();
        var helper = new RecipeDefinitionRegistrar();
        var input = new ItemStack(Items.PAPER, 1, 25101);
        var name = new ResourceLocation("fixed_recovery_test", "craft");
        var declaration = new ShapelessCraftingDefinition(SOURCE, name, "", ItemValue.of(input),
                List.of(ItemIngredient.exact(new ItemStack(Items.STICK))));
        var original = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(input).outputs(new ItemStack(Items.COAL))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        try {
            assertTrue(composition.replaceRecovery("material_owner", List.of(new RecyclingContribution(SOURCE,
                    new ItemAndMetadata(input), 1, Map.of(), Map.of(Materials.Copper, GTValues.M),
                    RecyclingRoutePolicy.REPLACE)), List.of(), List.of()).value().isPresent());
            var metadata = GregTechAPI.RECYCLING_MANAGER.getRecyclingData(input);
            var accepted = helper.replaceCrafting("helper", List.of(declaration), List.of(),
                    List.of(fixed(input, 2 * GTValues.M)), List.of(original));
            assertTrue(accepted.value().isPresent(), () -> accepted.diagnostics().toString());
            assertEquals(metadata, GregTechAPI.RECYCLING_MANAGER.getRecyclingData(input));
            var generated = helper.generatedRecovery("helper");
            assertEquals(1, generated.size());
            assertEquals(2, generated.getFirst().recipe().getOutputs().getFirst().getCount());
            var crafting = ForgeRegistries.RECIPES.getValue(name);
            var invalid = helper.replaceCrafting("helper", List.of(new DisabledCraftingDefinition(SOURCE, name)),
                    List.of(), List.of(fixed(input, -1)), List.of());
            assertTrue(invalid.value().isEmpty());
            assertSame(crafting, ForgeRegistries.RECIPES.getValue(name));
            assertEquals(generated, helper.generatedRecovery("helper"));
            assertEquals(metadata, GregTechAPI.RECYCLING_MANAGER.getRecyclingData(input));
            assertTrue(helper.replaceCrafting("helper", List.of()).value().isPresent());
            assertNull(RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of()));
            assertEquals(metadata, GregTechAPI.RECYCLING_MANAGER.getRecyclingData(input));
            assertTrue(helper.replaceCrafting("helper", List.of(declaration), List.of(),
                    List.of(fixed(input, GTValues.M)), List.of()).value().isPresent());
            assertTrue(helper.clear().value().isPresent());
            assertSame(original.recipe(), RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE,
                    List.of(input), List.of()));
            assertEquals(metadata, GregTechAPI.RECYCLING_MANAGER.getRecyclingData(input));
            assertFalse(ForgeRegistries.RECIPES.containsKey(name));
        } finally {
            assertTrue(helper.clear().value().isPresent());
            assertTrue(composition.clear().value().isPresent());
            original.map().removeRecipe(original.recipe());
        }
    }

    @Test
    void unrelatedChangesRetainGeneratedIdentitiesAndMapRevision() {
        var helper = new RecipeDefinitionRegistrar();
        var other = new RecipeDefinitionRegistrar();
        var first = new ItemStack(Items.PAPER, 1, 25102);
        var second = new ItemStack(Items.PAPER, 1, 25103);
        var secondDefinition = fixed(second, GTValues.M);
        try {
            assertTrue(helper.replaceCrafting("helper", List.of(), List.of(),
                    List.of(fixed(first, GTValues.M), secondDefinition), List.of()).value().isPresent());
            var before = helper.generatedRecovery("helper");
            var revision = RecipeMaps.MACERATOR_RECIPES.getRecipeRevision();
            assertTrue(other.clear().value().isPresent());
            assertEquals(before, helper.generatedRecovery("helper"));
            assertSame(before.getFirst().recipe(), helper.generatedRecovery("helper").getFirst().recipe());
            assertSame(before.getLast().recipe(), helper.generatedRecovery("helper").getLast().recipe());
            assertEquals(revision, RecipeMaps.MACERATOR_RECIPES.getRecipeRevision());
            assertTrue(helper.replaceCrafting("helper", List.of(), List.of(),
                    List.of(fixed(first, 2 * GTValues.M), secondDefinition), List.of()).value().isPresent());
            var after = helper.generatedRecovery("helper");
            assertNotSame(before.getFirst().recipe(), after.getFirst().recipe());
            assertSame(before.getLast().recipe(), after.getLast().recipe());
            assertTrue(helper.replaceCrafting("helper", List.of(), List.of(),
                    List.of(fixed(second, GTValues.M)), List.of()).value().isPresent());
            assertNotSame(after.getLast().recipe(), helper.generatedRecovery("helper").getFirst().recipe(),
                    "A fresh declaration explicitly requests regeneration against the current material environment");
        } finally {
            assertTrue(helper.clear().value().isPresent());
            assertTrue(other.clear().value().isPresent());
        }
    }

    @Test
    void additiveRecoveryDisplacesOnlyGeneratedEndpointsAndPreservesOtherMapRecipes() {
        var helper = new RecipeDefinitionRegistrar();
        var input = new ItemStack(Items.PAPER, 1, 25105);
        var macerator = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(input)
                .outputs(new ItemStack(Items.COAL)).duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        var extractor = RecipeMaps.EXTRACTOR_RECIPES.recipeBuilder().inputs(input)
                .outputs(new ItemStack(Items.BRICK, 4)).duration(300).EUt(7).buildAndRegisterWithReceipt()
                .orElseThrow();
        try {
            var definition = fixed(input, GTValues.M);
            long maceratorRevision = macerator.map().getRecipeRevision();
            long extractorRevision = extractor.map().getRecipeRevision();
            var conflicts = helper.recoveryConflicts(definition).value().orElseThrow();
            assertEquals(1, conflicts.size());
            assertSame(macerator.map(), conflicts.getFirst().map());
            assertSame(macerator.recipe(), conflicts.getFirst().recipe());
            assertEquals(maceratorRevision, macerator.map().getRecipeRevision());
            assertEquals(extractorRevision, extractor.map().getRecipeRevision());
            assertTrue(helper.recoveryConflicts(fixed(input, -1)).value().isEmpty());
            assertTrue(helper.replaceCrafting("helper", List.of(), List.of(), List.of(definition), conflicts)
                    .value().isPresent());
            assertEquals(1, helper.generatedRecovery("helper").size());
            assertSame(extractor.recipe(), extractor.map().findRecipe(8, List.of(input), List.of()));
            assertEquals(extractorRevision, extractor.map().getRecipeRevision());
            assertTrue(helper.clear().value().isPresent());
            assertSame(macerator.recipe(), macerator.map().findRecipe(8, List.of(input), List.of()));
            assertSame(extractor.recipe(), extractor.map().findRecipe(8, List.of(input), List.of()));
        } finally {
            assertTrue(helper.clear().value().isPresent());
            macerator.map().removeRecipe(macerator.recipe());
            extractor.map().removeRecipe(extractor.recipe());
        }
    }

    private static RecyclingRecipeDefinition fixed(ItemStack input, long amount) {
        return new RecyclingRecipeDefinition(SOURCE, ItemValue.of(input),
                List.of(new RecyclingMaterial(Materials.Iron, amount)), true, null);
    }

    @Test
    void retainedRecoveryStillRejectsExternalRemovalBeforeCraftingChanges() {
        var helper = new RecipeDefinitionRegistrar();
        var input = new ItemStack(Items.PAPER, 1, 25104);
        var definition = fixed(input, GTValues.M);
        var name = new ResourceLocation("fixed_recovery_test", "external_removal");
        NativeRecipeRegistration owned = null;
        try {
            assertTrue(helper.replaceCrafting("helper", List.of(), List.of(), List.of(definition), List.of())
                    .value().isPresent());
            owned = helper.generatedRecovery("helper").getFirst();
            assertTrue(owned.map().removeRecipe(owned.recipe()));
            var declaration = new ShapelessCraftingDefinition(SOURCE, name, "", ItemValue.of(input),
                    List.of(ItemIngredient.exact(new ItemStack(Items.STICK))));
            var rejected = helper.replaceCrafting("helper", List.of(declaration), List.of(), List.of(definition),
                    List.of());
            assertTrue(rejected.value().isEmpty());
            assertEquals("recipe.ownership.changed", rejected.diagnostics().getFirst().code());
            assertFalse(ForgeRegistries.RECIPES.containsKey(name));
        } finally {
            if (owned != null) assertTrue(owned.map().compileRecipe(owned.recipe()));
            assertTrue(helper.clear().value().isPresent());
        }
    }
}

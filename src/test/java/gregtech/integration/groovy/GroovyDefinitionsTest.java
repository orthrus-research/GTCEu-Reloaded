package gregtech.integration.groovy;

import gregtech.Bootstrap;
import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.cleanroommc.groovyscript.sandbox.LoadStage;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GroovyDefinitionsTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void groovyCanReplaceDisableAndReloadPreparedRecyclingRecipes() throws Exception {
        try (var unification = new RecyclingUnificationFixture()) {
            var errors = new ArrayList<DefinitionDiagnostic>();
            var frontend = new GroovyDefinitions(() -> LoadStage.POST_INIT, errors::add);
            var binding = new Binding();
            binding.setVariable("frontend", frontend);
            try {
                new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                        import gregtech.api.GTValues
                        import gregtech.api.definition.SourceOrigin
                        import gregtech.api.recipes.RecipeMaps
                        import gregtech.api.recipes.definition.*
                        import gregtech.api.unification.material.Materials
                        import net.minecraft.init.Items
                        import net.minecraft.item.ItemStack
                        import net.minecraft.nbt.NBTTagCompound

                        def input = new ItemStack(Items.PAPER)
                        def tag = new NBTTagCompound()
                        tag.setBoolean('groovyRecoveryTest', true)
                        input.setTagCompound(tag)
                        def source = new SourceOrigin('groovy', 'recovery.groovy', 1, 1, 'paper', 'postInit')
                        def definition = new RecyclingRecipeDefinition(source, ItemValue.of(input),
                                [new RecyclingMaterial(Materials.Iron, GTValues.M)], true, null)
                        def map = RecipeMaps.MACERATOR_RECIPES
                        assert frontend.replaceRecycling('recovery', [definition]).value().get().recipes() == 1
                        def old = map.findRecipe(Long.MAX_VALUE, [input], [])
                        assert old != null
                        def invalid = new RecyclingRecipeDefinition(source, definition.input(),
                                [new RecyclingMaterial(Materials.Iron, -1L)], true, null)
                        assert !frontend.replaceRecycling('recovery', [invalid]).value().present
                        assert map.findRecipe(Long.MAX_VALUE, [input], []).is(old)
                        assert frontend.replaceRecycling('recovery', []).value().present
                        assert map.findRecipe(Long.MAX_VALUE, [input], []) == null
                        assert frontend.replaceRecycling('recovery', [definition]).value().present
                        frontend.onReload()
                        assert map.findRecipe(Long.MAX_VALUE, [input], []) == null
                        """);
                assertEquals(1, errors.size());
                assertEquals("recipe.recycling.generation", errors.getFirst().code());
            } finally {
                frontend.onReload();
            }
        }
    }

    @Test
    void groovyCanDisplaceNativeRecoveryAndReloadRestoresTheExactBaseline() throws Exception {
        try (var unification = new RecyclingUnificationFixture()) {
            var errors = new ArrayList<DefinitionDiagnostic>();
            var frontend = new GroovyDefinitions(() -> LoadStage.POST_INIT, errors::add);
            var binding = new Binding();
            binding.setVariable("frontend", frontend);
            new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                    import gregtech.api.GTValues
                    import gregtech.api.definition.SourceOrigin
                    import gregtech.api.recipes.RecipeMaps
                    import gregtech.api.recipes.definition.*
                    import gregtech.api.unification.material.Materials
                    import net.minecraft.init.Items
                    import net.minecraft.item.ItemStack
                    import net.minecraft.nbt.NBTTagCompound

                    def input = new ItemStack(Items.PAPER)
                    def tag = new NBTTagCompound()
                    tag.setBoolean('groovyBaselineTest', true)
                    input.setTagCompound(tag)
                    def map = RecipeMaps.MACERATOR_RECIPES
                    def baseline = map.recipeBuilder().inputs(input).outputs(new ItemStack(Items.COAL))
                        .duration(20).EUt(8).buildAndRegisterWithReceipt().get()
                    try {
                        def source = new SourceOrigin('groovy', 'baseline.groovy', 1, 1, 'paper', 'postInit')
                        def definition = new RecyclingRecipeDefinition(source, ItemValue.of(input),
                                [new RecyclingMaterial(Materials.Iron, GTValues.M)], true, null)
                        assert frontend.replaceRecycling('recovery', [definition], [baseline]).value().present
                        assert !map.findRecipe(Long.MAX_VALUE, [input], []).is(baseline.recipe())
                        assert frontend.replaceRecycling('recovery', []).value().present
                        assert map.findRecipe(Long.MAX_VALUE, [input], []) == null
                        frontend.onReload()
                        assert map.findRecipe(Long.MAX_VALUE, [input], []).is(baseline.recipe())
                        assert frontend.replaceRecycling('recovery', [], [baseline]).value().present
                        frontend.onReload()
                        assert map.findRecipe(Long.MAX_VALUE, [input], []).is(baseline.recipe())
                    } finally {
                        frontend.onReload()
                        map.removeRecipe(baseline.recipe())
                    }
                    """);
            assertTrue(errors.isEmpty());
        }
    }

    @Test
    void frontendUsesTheSharedRegistrarAndItsReloadCallbackRestoresTheBaseline() {
        RecipeMap<?> map = new RecipeMapBuilder<>("groovy_owned_test", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        AtomicReference<LoadStage> stage = new AtomicReference<>(LoadStage.PRE_INIT);
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        GroovyDefinitions frontend = new GroovyDefinitions(stage::get, errors::add);
        var definition = new RecipeDefinition(new SourceOrigin("groovy", "metals.groovy", 3, 1, "iron", "postInit"),
                map.unlocalizedName, List.of(ItemIngredient.exact(new ItemStack(Items.IRON_INGOT))),
                List.of(ItemValue.of(new ItemStack(Items.GOLD_INGOT))), 20, 8, false);
        assertThrows(IllegalStateException.class, () -> frontend.replace("metals", List.of(definition)));
        assertTrue(map.getRecipeList().isEmpty());
        stage.set(LoadStage.POST_INIT);
        for (int run = 0; run < 3; run++) {
            assertTrue(frontend.replace("metals", List.of(definition)).value().isPresent());
            assertNotNull(map.findRecipe(8, List.of(new ItemStack(Items.IRON_INGOT)), List.of()));
            var invalid = new RecipeDefinition(definition.origin(), definition.recipeMap(), definition.inputs(),
                    definition.outputs(), 0, 8, false);
            assertTrue(frontend.replace("metals", List.of(invalid)).value().isEmpty());
            assertEquals(1, map.getRecipeList().size());
            frontend.onReload();
            assertTrue(map.getRecipeList().isEmpty());
            assertNull(map.findRecipe(8, List.of(new ItemStack(Items.IRON_INGOT)), List.of()));
        }
        assertEquals(3, errors.size());
        assertTrue(errors.stream().allMatch(d -> d.code().equals("recipe.duration")));
    }

    @Test
    void groovyPublishesCompositionAndGeneratedRecoveryTogetherAndReloadClearsBoth() throws Exception {
        try (var unification = new RecyclingUnificationFixture()) {
            var errors = new ArrayList<DefinitionDiagnostic>();
            var frontend = new GroovyDefinitions(() -> LoadStage.POST_INIT, errors::add);
            var binding = new Binding();
            binding.setVariable("frontend", frontend);
            new GroovyShell(getClass().getClassLoader(), binding).evaluate(
                    """
                            import gregtech.api.GTValues
                            import gregtech.api.GregTechAPI
                            import gregtech.api.definition.SourceOrigin
                            import gregtech.api.recipes.*
                            import gregtech.api.recipes.definition.*
                            import gregtech.api.unification.material.Materials
                            import gregtech.api.unification.stack.ItemAndMetadata
                            import net.minecraft.init.Items
                            import net.minecraft.item.ItemStack

                            def input = new ItemStack(Items.DYE, 1, 32000)
                            def key = new ItemAndMetadata(input)
                            def manager = GregTechAPI.RECYCLING_MANAGER
                            def before = manager.snapshot().get(key)
                            def map = RecipeMaps.MACERATOR_RECIPES
                            def source = new SourceOrigin('groovy', 'combined.groovy', 1, 1, 'combined', 'postInit')
                            def route = { amount -> new RecyclingContribution(source, key, 1, [:],
                                [(Materials.Iron): amount], RecyclingRoutePolicy.REPLACE) }
                            def policy = new RecyclingRecoveryDefinition(source, ItemValue.of(input), true, null)
                            try {
                                assert frontend.replaceRecovery('combined', [route(2L * GTValues.M)], [policy], [])
                                    .value().get().recipes() == 1
                                def old = map.findRecipe(Long.MAX_VALUE, [input], [])
                                assert old.outputs[0].count == 2
                                assert manager.getRecyclingData(key).materials[0].amount == 2L * GTValues.M
                                assert !frontend.replaceRecovery('combined', [route(Long.MAX_VALUE)], [policy], []).value().present
                                assert map.findRecipe(Long.MAX_VALUE, [input], []).is(old)
                                assert manager.getRecyclingData(key).materials[0].amount == 2L * GTValues.M
                                assert frontend.replaceRecovery('combined', [], [], []).value().get().recipes() == 0
                                assert map.findRecipe(Long.MAX_VALUE, [input], []) == null
                                assert manager.snapshot().get(key) == before
                                assert frontend.replaceRecovery('combined', [route(GTValues.M as long)], [policy], []).value().present
                                frontend.onReload()
                                assert map.findRecipe(Long.MAX_VALUE, [input], []) == null
                                assert manager.snapshot().get(key) == before
                            } finally { frontend.onReload() }
                            """);
            assertEquals(1, errors.size());
            assertEquals("recipe.recycling.generation", errors.getFirst().code());
        }
    }
}

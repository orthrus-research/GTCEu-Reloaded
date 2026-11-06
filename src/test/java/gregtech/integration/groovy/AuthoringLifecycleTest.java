package gregtech.integration.groovy;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecipeDefinition;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.groovyscript.compat.mods.GroovyContainer;
import com.cleanroommc.groovyscript.compat.mods.GroovyPropertyContainer;
import com.cleanroommc.groovyscript.sandbox.LoadStage;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringLifecycleTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void cachedGroovyAuthorReadsAcceptedStateAndStartsEmptyAfterReload() throws Exception {
        for (var annotation : List.of("", "@groovy.transform.CompileStatic")) {
            var stage = new java.util.concurrent.atomic.AtomicReference<>(LoadStage.PRE_INIT);
            var frontend = new GroovyDefinitions(stage::get, diagnostic -> fail(diagnostic.format()));
            assertThrows(IllegalStateException.class, () -> frontend.craftingGroup("craft"));
            stage.set(LoadStage.POST_INIT);
            assertTrue(frontend.craftingGroup("craft").isEmpty());
            assertThrows(NullPointerException.class, () -> frontend.craftingGroup(null));
            var source = new SourceOrigin("groovy", "accepted_crafting.groovy", 1, 1, "craft", "postInit");
            var first = new net.minecraft.util.ResourceLocation("groovy_accepted_crafting_test", "first");
            var second = new net.minecraft.util.ResourceLocation("groovy_accepted_crafting_test", "second");
            var recipe = new gregtech.api.recipes.definition.ShapelessCraftingDefinition(source, first, "",
                    ItemValue.of(new ItemStack(Items.DIAMOND)),
                    List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
            var another = new gregtech.api.recipes.definition.ShapelessCraftingDefinition(source, second, "",
                    ItemValue.of(new ItemStack(Items.GOLD_INGOT)),
                    List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
            try (var loader = new groovy.lang.GroovyClassLoader(getClass().getClassLoader())) {
                var author = loader.parseClass("""
                        import gregtech.integration.groovy.GroovyDefinitions
                        import gregtech.api.recipes.definition.*
                        """ + annotation +
                        """

                                class AcceptedCraftingAuthor {
                                    static void run(GroovyDefinitions frontend, CraftingDefinition first,
                                                    CraftingDefinition second) {
                                        assert frontend.craftingGroup('craft').isEmpty()
                                        assert frontend.replaceCrafting('craft', [first]).value().isPresent()
                                        def nativeFirst = net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(first.name())
                                        CraftingGroupDefinition accepted = frontend.craftingGroup('craft').orElseThrow()
                                        List<CraftingMutation> next = new ArrayList<>(accepted.declarations())
                                        next.add(second)
                                        assert frontend.replaceCrafting('craft', next, accepted.contributions(),
                                            accepted.recovery(), []).value().isPresent()
                                        assert frontend.craftingGroup('craft').orElseThrow().declarations().size() == 2
                                        assert nativeFirst.is(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(first.name()))
                                        assert accepted.declarations().size() == 1
                                        assert frontend.replaceCrafting('craft', [new DisabledCraftingDefinition(
                                            first.origin(), first.name())]).value().isPresent()
                                        assert frontend.craftingGroup('craft').orElseThrow().declarations()[0] instanceof DisabledCraftingDefinition
                                    }
                                }
                                """);
                var run = author.getMethod("run", GroovyDefinitions.class,
                        gregtech.api.recipes.definition.CraftingDefinition.class,
                        gregtech.api.recipes.definition.CraftingDefinition.class);
                for (int i = 0; i < 2; i++) {
                    run.invoke(null, frontend, recipe, another);
                    assertTrue(
                            net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(first).isDynamic());
                    assertTrue(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(second)
                            .isDynamic());
                    frontend.onReload();
                    assertTrue(frontend.craftingGroup("craft").isEmpty());
                    assertFalse(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(first));
                    assertFalse(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(second));
                }
            } finally {
                frontend.onReload();
            }
        }
    }

    @Test
    void earlyReloadRestoresTypedBaselineBeforeTheImperativeCallback() throws Exception {
        var field = GroovyScriptModule.class.getDeclaredField("modSupportContainer");
        field.setAccessible(true);
        var previous = field.get(null);
        var properties = new PropertyContainer();
        var registrar = new gregtech.api.recipes.RecipeDefinitionRegistrar();
        var ownerField = GroovyDefinitions.class.getDeclaredField("registrar");
        ownerField.setAccessible(true);
        ownerField.set(properties.definitions, registrar);
        var source = new SourceOrigin("groovy", "early_reload.groovy", 1, 1, "recipe", "postInit");
        var name = new net.minecraft.util.ResourceLocation("groovy_early_reload_test", "recipe");
        var definition = new gregtech.api.recipes.definition.ShapelessCraftingDefinition(source, name, "",
                ItemValue.of(new ItemStack(Items.DIAMOND)), List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
        var original = gregtech.api.recipes.CraftingDefinitionCompiler.compile(definition).value().orElseThrow();
        var seed = gregtech.api.recipes.CraftingRegistryTransaction.prepare(java.util.Map.of(name, original));
        seed.publish();
        var container = new GroovyContainer<PropertyContainer>() {

            public PropertyContainer get() {
                return properties;
            }

            public String getModId() {
                return "gregtech";
            }

            public String getContainerName() {
                return "early_reload_test";
            }

            public boolean isLoaded() {
                return true;
            }

            public void onCompatLoaded(GroovyContainer<?> ignored) {}
        };
        field.set(null, container);
        try {
            assertTrue(registrar.replaceCrafting("craft", List.of(definition)).value().isPresent());
            assertNotSame(original, net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(name));
            GroovyScriptModule.restoreDefinitionsBeforeReload();
            assertSame(original, net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(name));
            assertTrue(registrar.groups().isEmpty());
            // The ordinary container callback runs later and must not undo another registry's intervening cleanup.
            seed.restore();
            properties.definitions.onReload();
            assertFalse(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(name));
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            if (net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(name))
                seed.restoration().prepare().publish();
            field.set(null, previous);
        }
    }

    @Test
    void groovyFacadeOwnsCraftingMutationsAndRestoresItsBaseline() {
        for (var annotation : List.of("", "@groovy.transform.CompileStatic")) {
            var frontend = new GroovyDefinitions(() -> LoadStage.POST_INIT, diagnostic -> fail(diagnostic.format()));
            var source = new SourceOrigin("groovy", "owned_crafting.groovy", 1, 1, "craft", "postInit");
            var name = new net.minecraft.util.ResourceLocation("groovy_owned_crafting_test", "recipe");
            var definition = new gregtech.api.recipes.definition.ShapelessCraftingDefinition(source, name, "",
                    ItemValue.of(new ItemStack(Items.DIAMOND)),
                    List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
            var disabled = new gregtech.api.recipes.definition.DisabledCraftingDefinition(source, name);
            var binding = new Binding();
            binding.setVariable("frontend", frontend);
            binding.setVariable("definition", definition);
            binding.setVariable("disabled", disabled);
            try {
                new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                        import gregtech.integration.groovy.GroovyDefinitions
                        import gregtech.api.recipes.definition.*
                        """ + annotation +
                        """

                                class CraftingGroupAuthor {
                                    static void run(GroovyDefinitions frontend, CraftingDefinition definition,
                                                    DisabledCraftingDefinition disabled) {
                                        assert frontend.replaceCrafting('craft', [definition]).value().isPresent()
                                        assert frontend.replaceCrafting('craft', [disabled]).value().isPresent()
                                        assert frontend.replaceCrafting('craft', [definition]).value().isPresent()
                                        assert frontend.replaceCrafting('craft', Collections.<CraftingMutation>emptyList()).value().isPresent()
                                        assert frontend.clear().value().isPresent()
                                    }
                                }
                                CraftingGroupAuthor.run(frontend, definition, disabled)
                                """);
                assertFalse(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(name));
            } finally {
                assertTrue(frontend.clear().value().isPresent());
            }
        }
    }

    @Test
    void dynamicAndStaticallyCheckedGroovyCompileTheSameNativeCraftingDefinition() {
        for (var annotation : List.of("", "@groovy.transform.CompileStatic")) {
            var value = new GroovyShell(getClass().getClassLoader()).evaluate("""
                    import gregtech.api.definition.SourceOrigin
                    import gregtech.api.recipes.CraftingDefinitionCompiler
                    import gregtech.api.recipes.definition.*
                    import net.minecraft.init.Items
                    import net.minecraft.item.ItemStack
                    import net.minecraft.item.crafting.IRecipe
                    import net.minecraft.util.ResourceLocation
                    """ + annotation + """

                    class CraftingAuthor {
                        static IRecipe compile() {
                            List<ItemInput> inputs = new ArrayList<>()
                            inputs.add(ItemIngredient.exact(new ItemStack(Items.COAL)))
                            inputs.add(null)
                            def source = new SourceOrigin('groovy', 'typed_crafting.groovy', 1, 1, 'recipe', 'postInit')
                            def definition = new ShapedCraftingDefinition(source,
                                new ResourceLocation('crafting_groovy_test', 'recipe'), 'typed_group',
                                ItemValue.of(new ItemStack(Items.EMERALD)), 2, 1, true, inputs)
                            return CraftingDefinitionCompiler.compile(definition).value().orElseThrow()
                        }
                    }
                    CraftingAuthor.compile()
                    """);
            var recipe = (net.minecraft.item.crafting.IRecipe) value;
            assertEquals(Items.EMERALD, recipe.getRecipeOutput().getItem());
            assertEquals(2, recipe.getIngredients().size());
            assertTrue(recipe.getIngredients().get(1).apply(ItemStack.EMPTY));
            assertEquals("typed_group", recipe.getGroup());
        }
    }

    @Test
    void groovyCanCreateOverwriteDisableAndReenableTypedGroups() {
        var map = new RecipeMapBuilder<>("script_typed_lifecycle", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        var frontend = new GroovyDefinitions(() -> LoadStage.POST_INIT, diagnostic -> fail(diagnostic.format()));
        var origin = new SourceOrigin("groovy", "lifecycle.groovy", 1, 1, "smelting", "postInit");
        var inputs = List.of(ItemIngredient.exact(new ItemStack(Items.IRON_INGOT)));
        var first = new RecipeDefinition(origin, map.unlocalizedName, inputs,
                List.of(ItemValue.of(new ItemStack(Items.GOLD_INGOT))), 20, 8, false);
        var revised = new RecipeDefinition(origin, map.unlocalizedName, inputs,
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 40, 8, false);
        var binding = new Binding();
        binding.setVariable("frontend", frontend);
        binding.setVariable("map", map);
        binding.setVariable("first", first);
        binding.setVariable("revised", revised);
        new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                assert frontend.replace('pack_recipes', [first]).value().present
                assert map.recipeList.size() == 1
                assert map.recipeList.iterator().next().duration == 20
                assert frontend.replace('pack_recipes', [revised]).value().present
                assert map.recipeList.size() == 1
                assert map.recipeList.iterator().next().duration == 40
                assert map.recipeList.iterator().next().outputs[0].item == net.minecraft.init.Items.DIAMOND
                // Deliberately disable the group; no malformed recipe is needed to express removal.
                assert frontend.replace('pack_recipes', []).value().present
                assert map.recipeList.empty
                assert frontend.replace('pack_recipes', [first]).value().present
                assert map.recipeList.size() == 1
                frontend.onReload()
                assert map.recipeList.empty
                """);
    }

    @Test
    void imperativeGroovyRetainsRemovalOverwriteClearAndNullOmittedChannels() throws Exception {
        var containerField = GroovyScriptModule.class.getDeclaredField("modSupportContainer");
        containerField.setAccessible(true);
        var previous = containerField.get(null);
        var properties = new GroovyPropertyContainer();
        var container = new GroovyContainer<GroovyPropertyContainer>() {

            public GroovyPropertyContainer get() {
                return properties;
            }

            public String getModId() {
                return "gregtech";
            }

            public String getContainerName() {
                return "lifecycle_test";
            }

            public boolean isLoaded() {
                return true;
            }

            public void onCompatLoaded(GroovyContainer<?> ignored) {}
        };
        containerField.set(null, container);
        try {
            var map = new RecipeMapBuilder<>("script_imperative_lifecycle", new SimpleRecipeBuilder())
                    .itemInputs(1).itemOutputs(1).fluidInputs(1).build();
            var binding = new Binding();
            binding.setVariable("map", map);
            binding.setVariable("adapter", new VirtualizedRecipeMap(map));
            binding.setVariable("iron", new ItemStack(Items.IRON_INGOT));
            binding.setVariable("gold", new ItemStack(Items.GOLD_INGOT));
            binding.setVariable("diamond", new ItemStack(Items.DIAMOND));
            binding.setVariable("water", new FluidStack(FluidRegistry.WATER, 1000));
            new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                    map.recipeBuilder().inputs(iron).outputs(gold).duration(20).EUt(8).buildAndRegister()
                    assert adapter.find(8, [iron], null) != null
                    assert adapter.removeByInput(8, [iron], null)
                    assert adapter.find(8, [iron], null) == null
                    // Explicit replacement retains normal Groovy remove-and-register semantics.
                    map.recipeBuilder().inputs(iron).outputs(diamond).duration(40).EUt(8).buildAndRegister()
                    assert adapter.find(8, [iron], null).outputs[0].item == diamond.item
                    map.recipeBuilder().fluidInputs(water).outputs(gold).duration(20).EUt(8).buildAndRegister()
                    assert adapter.find(8, null, [water]) != null
                    assert adapter.removeByInput(8, null, [water])
                    assert adapter.find(8, null, [water]) == null
                    gregtech.api.recipes.GTRecipeHandler.removeAllRecipes(map)
                    assert map.recipeList.empty
                    """);
        } finally {
            containerField.set(null, previous);
        }
    }
}

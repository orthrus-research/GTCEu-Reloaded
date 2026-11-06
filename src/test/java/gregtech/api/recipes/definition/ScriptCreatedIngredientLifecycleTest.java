package gregtech.api.recipes.definition;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeDefinitionRegistrar;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCreatedIngredientLifecycleTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("groovy", "late_content.groovy", 4, 1,
            "late_recipe", "postInit");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void sameDeclarationCanBeRetriedAfterGroovyCreatesItsIngredient(boolean checked) {
        String name = "scriptCreatedIngredient" + (checked ? "Checked" : "Dynamic");
        RecipeMap<?> map = new RecipeMapBuilder<>("script_created_ingredient_" + checked, new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        var registrar = new RecipeDefinitionRegistrar();
        var baseline = new RecipeDefinition(ORIGIN, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.COAL))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 40, 8, false);
        assertTrue(registrar.replace(name, List.of(baseline)).value().isPresent());
        Recipe previous = lookup(map, new ItemStack(Items.COAL));
        assertNotNull(previous);

        Binding variables = new Binding();
        variables.setVariable("source", ORIGIN);
        variables.setVariable("mapName", map.unlocalizedName);
        variables.setVariable("oreName", name);
        variables.setVariable("output", new ItemStack(Items.EMERALD));
        variables.setVariable("member", new ItemStack(Items.PAPER));
        var shell = new GroovyShell(getClass().getClassLoader(), variables);
        shell.evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.definition.SourceOrigin
                import gregtech.api.recipes.definition.*
                import net.minecraft.item.ItemStack
                import net.minecraftforge.oredict.OreDictionary
                %s
                class LateContentAuthor {
                    static RecipeDefinition declare(SourceOrigin source, String map, String ore, ItemStack output) {
                        new RecipeDefinition(source, map,
                            List.<ItemInput>of(new OreIngredient(ore, 2, true, NbtPolicy.any())),
                            List.of(ItemValue.of(output)), 40, 8L, false)
                    }
                    static void create(String ore, ItemStack member) {
                        OreDictionary.registerOre(ore, member)
                    }
                }
                declaration = LateContentAuthor.declare(source, mapName, oreName, output)
                """.formatted(checked ? "@CompileStatic" : ""));
        var declaration = (RecipeDefinition) variables.getVariable("declaration");
        try {
            assertFalse(OreDictionary.doesOreNameExist(name));
            long revision = map.getRecipeRevision();
            var early = registrar.replace(name, List.of(declaration));
            assertTrue(early.value().isEmpty());
            assertEquals(List.of("ingredient.ore.unknown"), early.diagnostics().stream().map(d -> d.code()).toList());
            assertEquals(ORIGIN, early.diagnostics().getFirst().origin());
            assertEquals("inputs[0].oreName", early.diagnostics().getFirst().field());
            assertFalse(OreDictionary.doesOreNameExist(name), "A failed attempt must not allocate an ore name");
            assertEquals(revision, map.getRecipeRevision());
            assertSame(previous, lookup(map, new ItemStack(Items.COAL)));

            shell.evaluate("LateContentAuthor.create(oreName, member)");
            assertTrue(registrar.replace(name, List.of(declaration)).value().isPresent());
            Recipe accepted = lookup(map, new ItemStack(Items.PAPER, 2));
            assertNotNull(accepted);
            assertEquals(Items.EMERALD, accepted.getOutputs().getFirst().getItem());
            assertNull(lookup(map, new ItemStack(Items.PAPER)), "Late resolution must retain the required quantity");
            assertNull(lookup(map, new ItemStack(Items.COAL)));

            variables.setVariable("member", new ItemStack(Items.STICK));
            shell.evaluate("LateContentAuthor.create(oreName, member)");
            assertSame(accepted, lookup(map, new ItemStack(Items.STICK, 2)),
                    "Native lookup must see later Groovy members without rebuilding the recipe");

            assertTrue(registrar.replace(name, List.of()).value().isPresent());
            assertTrue(map.getRecipeList().isEmpty());
            assertNull(lookup(map, new ItemStack(Items.PAPER, 2)));
            assertTrue(registrar.replace(name, List.of(declaration)).value().isPresent());
            assertNotNull(lookup(map, new ItemStack(Items.PAPER, 2)));
            assertNotNull(lookup(map, new ItemStack(Items.STICK, 2)));
        } finally {
            assertTrue(registrar.replace(name, List.of()).value().isPresent());
        }
    }

    private static Recipe lookup(RecipeMap<?> map, ItemStack stack) {
        return map.findRecipe(8, List.of(stack), List.of(), true);
    }
}

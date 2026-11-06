package gregtech.api.recipes.definition;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeDefinitionCompiler;
import gregtech.api.recipes.RecipeDefinitionRegistrar;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RichRecipeDefinitionTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("test", "rich.groovy", 8, 2, "wash", "postInit");
    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void fluidOnlyRecipesOwnTagsAndRespectQuantityThroughLookupAndConsumption() {
        RecipeMap<?> map = map(0, 0, 2, 1);
        FluidStack input = water(1000, 7);
        FluidStack output = new FluidStack(FluidRegistry.LAVA, 250);
        FluidValue inputValue = FluidValue.of(input);
        RecipeDefinition declaration = definition(map, List.of(), List.of(),
                List.of(new FluidIngredient(inputValue, true)), List.of(FluidValue.of(output)), List.of(), List.of());
        int hash = declaration.hashCode();
        input.tag.setInteger("grade", 99);
        input.amount = 1;
        output.amount = 1;
        inputValue.stack().tag.setInteger("grade", 42);
        assertEquals(hash, declaration.hashCode());
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        assertTrue(registrar.replace("fluids", List.of(declaration)).value().isPresent());
        Recipe recipe = map.findRecipe(8, List.of(), List.of(water(1000, 7)), true);
        assertNotNull(recipe);
        assertEquals(250, recipe.getFluidOutputs().getFirst().amount);
        assertNull(map.findRecipe(8, List.of(), List.of(water(999, 7)), true));
        assertNull(map.findRecipe(8, List.of(), List.of(water(2000, 99)), true));
        assertNull(map.findRecipe(8, List.of(), List.of(new FluidStack(FluidRegistry.WATER, 2000)), true));
        var tanks = new ArrayList<>(List.of(water(1200, 7)));
        assertTrue(recipe.matches(true, List.of(), tanks));
        assertEquals(200, tanks.getFirst().amount);
        assertTrue(registrar.replace("fluids", List.of()).value().isPresent());
        assertNull(map.findRecipe(8, List.of(), List.of(water(1000, 7)), true));
    }

    @Test
    void mixedOreCircuitAndFluidCatalystsUseNativeSemantics() {
        MetaItem<?>.MetaValueItem previous = MetaItems.INTEGRATED_CIRCUIT;
        MetaItem<?> carrier = MetaItems.ITEMS.getFirst();
        MetaItems.INTEGRATED_CIRCUIT = carrier.getItem("circuit.integrated");
        if (MetaItems.INTEGRATED_CIRCUIT == null) {
            MetaItems.INTEGRATED_CIRCUIT = carrier.addItem(461, "circuit.integrated");
        }
        try {
            String ore = "definitionTestMetal" + IDS.incrementAndGet();
            OreDictionary.registerOre(ore, new ItemStack(Items.IRON_INGOT));
            OreDictionary.registerOre(ore, new ItemStack(Items.GOLD_INGOT));
            RecipeMap<?> map = map(2, 1, 1, 0);
            RecipeDefinition declaration = definition(map,
                    List.of(new OreIngredient(ore, 2, true, NbtPolicy.any()), new CircuitIngredient(3)),
                    List.of(ItemValue.of(new ItemStack(Items.DIAMOND))),
                    List.of(new FluidIngredient(FluidValue.of(new FluidStack(FluidRegistry.WATER, 100)), false)),
                    List.of(), List.of(), List.of());
            RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
            assertTrue(registrar.replace("mixed", List.of(declaration)).value().isPresent());
            for (var item : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) {
                ItemStack metal = new ItemStack(item, 3);
                ItemStack circuit = IntCircuitIngredient.getIntegratedCircuit(3);
                var inventory = new ArrayList<>(List.of(metal, circuit));
                var tanks = new ArrayList<>(List.of(new FluidStack(FluidRegistry.WATER, 150)));
                Recipe recipe = map.findRecipe(8, inventory, tanks, true);
                assertNotNull(recipe);
                assertTrue(recipe.matches(true, inventory, tanks));
                assertEquals(1, metal.getCount());
                assertEquals(1, circuit.getCount());
                assertEquals(150, tanks.getFirst().amount);
                assertNull(map.findRecipe(8, List.of(new ItemStack(item, 2),
                        IntCircuitIngredient.getIntegratedCircuit(4)), tanks, true));
            }
            assertTrue(registrar.replace("mixed", List.of()).value().isPresent());
            assertTrue(map.getRecipeList().isEmpty());
        } finally {
            MetaItems.INTEGRATED_CIRCUIT = previous;
        }
    }

    @Test
    void checkedGroovyChanceDeclarationsEqualJavaAndCompileToNativeChanceAlgorithms() {
        RecipeMap<?> map = map(1, 2, 0, 1);
        ItemStack input = new ItemStack(Items.IRON_INGOT);
        ItemStack output = new ItemStack(Items.DIAMOND, 2);
        FluidStack fluid = new FluidStack(FluidRegistry.LAVA, 125);
        RecipeDefinition java = definition(map, List.of(ItemIngredient.exact(input)), List.of(), List.of(), List.of(),
                List.of(new ChancedItemDefinition(ItemValue.of(output), 10_000, 250)),
                List.of(new ChancedFluidDefinition(FluidValue.of(fluid), 10_000, 100)));
        Binding variables = new Binding();
        variables.setVariable("origin", ORIGIN);
        variables.setVariable("mapName", map.unlocalizedName);
        variables.setVariable("input", input);
        variables.setVariable("output", output);
        variables.setVariable("fluid", fluid);
        RecipeDefinition groovy = (RecipeDefinition) new GroovyShell(getClass().getClassLoader(), variables)
                .evaluate("""
                        import groovy.transform.CompileStatic
                        import gregtech.api.definition.SourceOrigin
                        import gregtech.api.recipes.definition.*
                        import net.minecraft.item.ItemStack
                        import net.minecraftforge.fluids.FluidStack
                        @CompileStatic
                        class RichAuthor {
                            static RecipeDefinition make(SourceOrigin source, String map, ItemStack input,
                                                         ItemStack output, FluidStack fluid) {
                                new RecipeDefinition(source, map, List.<ItemInput>of(ItemIngredient.exact(input)),
                                    List.<ItemValue>of(), List.<FluidIngredient>of(), List.<FluidValue>of(),
                                    List.of(new ChancedItemDefinition(ItemValue.of(output), 10000, 250)),
                                    List.of(new ChancedFluidDefinition(FluidValue.of(fluid), 10000, 100)),
                                    ChanceMode.OR, ChanceMode.OR, 40, 8L, false)
                            }
                        }
                        RichAuthor.make(origin, mapName, input, output, fluid)
                        """);
        assertEquals(java, groovy);
        Recipe recipe = RecipeDefinitionCompiler.compile(groovy).value().orElseThrow();
        assertSame(ChancedOutputLogic.OR, recipe.getChancedOutputs().getChancedOutputLogic());
        assertEquals(250, recipe.getChancedOutputs().getChancedEntries().getFirst().getChanceBoost());
        assertEquals(100, recipe.getChancedFluidOutputs().getChancedEntries().getFirst().getChanceBoost());
        assertEquals(2, recipe.getResultItemOutputs(1, 1, map).getFirst().getCount());
        assertEquals(125, recipe.getResultFluidOutputs(1, 1, map).getFirst().amount);
        output.setCount(1);
        fluid.amount = 1;
        assertEquals(2, recipe.getResultItemOutputs(1, 1, map).getFirst().getCount());
        assertEquals(125, recipe.getResultFluidOutputs(1, 1, map).getFirst().amount);
    }

    @Test
    void invalidRichReplacementReportsSourcePathsAndPreservesExistingRegistration() {
        RecipeMap<?> map = map(1, 1, 1, 1);
        RecipeDefinition valid = definition(map, List.of(ItemIngredient.exact(new ItemStack(Items.IRON_INGOT))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), List.of(), List.of(), List.of(), List.of());
        RecipeDefinitionRegistrar registrar = new RecipeDefinitionRegistrar();
        assertTrue(registrar.replace("checked", List.of(valid)).value().isPresent());
        Recipe existing = map.getRecipeList().iterator().next();
        long revision = map.getRecipeRevision();
        RecipeDefinition invalid = definition(map,
                List.of(new OreIngredient("missingDefinitionOre", 0, true, NbtPolicy.any()), new CircuitIngredient(33)),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))),
                List.of(FluidIngredient.exact(new FluidStack(FluidRegistry.WATER, 0))),
                List.of(FluidValue.of(new FluidStack(FluidRegistry.LAVA, -1))),
                List.of(new ChancedItemDefinition(ItemValue.of(ItemStack.EMPTY), 0, 0)),
                List.of(new ChancedFluidDefinition(FluidValue.of(new FluidStack(FluidRegistry.LAVA, 1)), 10_001, 0)));
        var result = registrar.replace("checked", List.of(invalid));
        assertTrue(result.value().isEmpty());
        assertEquals(List.of("ingredient.amount", "ingredient.ore.unknown", "ingredient.circuit.range",
                "ingredient.amount", "ingredient.amount", "recipe.output.empty", "recipe.chance.range",
                "recipe.chance.range", "recipe.inputs.limit", "recipe.outputs.limit", "recipe.fluidOutputs.limit"),
                result.diagnostics().stream().map(d -> d.code()).toList());
        result.diagnostics().forEach(d -> assertEquals(ORIGIN, d.origin()));
        assertEquals("fluidInputs[0].amount", result.diagnostics().get(3).field());
        assertEquals(revision, map.getRecipeRevision());
        assertSame(existing, map.findRecipe(8, List.of(new ItemStack(Items.IRON_INGOT)), List.of(), true));
        assertFalse(OreDictionary.doesOreNameExist("missingDefinitionOre"), "Validation must not allocate an ore ID");
        assertTrue(registrar.replace("checked", List.of()).value().isPresent());
    }

    @Test
    void absentAndEmptyFluidTagsAreDistinctAndNonconsumableInputsStillRequireQuantity() {
        FluidStack absent = new FluidStack(FluidRegistry.WATER, 100);
        FluidStack empty = absent.copy();
        empty.tag = new NBTTagCompound();
        assertNotEquals(FluidValue.of(absent), FluidValue.of(empty));
        var ingredient = new FluidIngredient(FluidValue.of(empty), false).compile();
        assertFalse(ingredient.acceptsFluid(absent));
        assertTrue(ingredient.acceptsFluid(empty));
        RecipeMap<?> map = map(0, 0, 1, 1);
        Recipe recipe = RecipeDefinitionCompiler.compile(definition(map, List.of(), List.of(),
                List.of(new FluidIngredient(FluidValue.of(empty), false)),
                List.of(FluidValue.of(new FluidStack(FluidRegistry.LAVA, 1))), List.of(), List.of())).value()
                .orElseThrow();
        FluidStack shortTank = empty.copy();
        shortTank.amount = 99;
        assertFalse(recipe.matches(false, List.of(), List.of(shortTank)));
        assertTrue(recipe.matches(true, List.of(), List.of(empty)));
        assertEquals(100, empty.amount);
    }

    private static RecipeMap<?> map(int items, int outputs, int fluids, int fluidOutputs) {
        return new RecipeMapBuilder<>("rich_definition_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(items).itemOutputs(outputs).fluidInputs(fluids).fluidOutputs(fluidOutputs).build();
    }

    private static RecipeDefinition definition(RecipeMap<?> map, List<ItemInput> inputs, List<ItemValue> outputs,
                                               List<FluidIngredient> fluids, List<FluidValue> fluidOutputs,
                                               List<ChancedItemDefinition> chances,
                                               List<ChancedFluidDefinition> fluidChances) {
        return new RecipeDefinition(ORIGIN, map.unlocalizedName, inputs, outputs, fluids, fluidOutputs, chances,
                fluidChances, ChanceMode.OR, ChanceMode.OR, 40, 8, false);
    }

    private static FluidStack water(int amount, int grade) {
        FluidStack stack = new FluidStack(FluidRegistry.WATER, amount);
        stack.tag = new NBTTagCompound();
        stack.tag.setInteger("grade", grade);
        return stack;
    }
}

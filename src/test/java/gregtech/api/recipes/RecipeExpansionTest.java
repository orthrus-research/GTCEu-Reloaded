package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.builders.CircuitAssemblerRecipeBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.builders.UniversalDistillationRecipeBuilder;
import gregtech.api.recipes.definition.*;
import gregtech.api.unification.material.Materials;
import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeExpansionTest {

    private MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeEach
    void supplyCircuitFixture() {
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        var carrier = MetaItems.ITEMS.getFirst();
        MetaItems.INTEGRATED_CIRCUIT = carrier.getItem("circuit.integrated");
        if (MetaItems.INTEGRATED_CIRCUIT == null)
            MetaItems.INTEGRATED_CIRCUIT = carrier.addItem(461, "circuit.integrated");
    }

    @AfterEach
    void restoreCircuitFixture() {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
    }

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final SourceOrigin SOURCE = new SourceOrigin("java", "ExpansionRecipes.java", 10, 1, "expanded",
            "recipes");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void cutterUsesNativeFluidAmountsAndAllVariantsAreOwned() {
        var registrar = new RecipeDefinitionRegistrar();
        var map = RecipeMaps.CUTTER_RECIPES;
        int baseline = map.getRecipeList().size();
        try {
            assertEquals(3, registrar.replace("cutter", List.of(itemDefinition(map))).value().orElseThrow().recipes());
            assertEquals(baseline + 3, map.getRecipeList().size());
            for (var expected : List.of(new Object[] { Materials.Water, 10, 200 },
                    new Object[] { Materials.DistilledWater, 7, 150 }, new Object[] { Materials.Lubricant, 2, 100 })) {
                var material = (gregtech.api.unification.material.Material) expected[0];
                Recipe found = map.findRecipe(32, List.of(new ItemStack(Items.APPLE)),
                        List.of(material.getFluid((int) expected[1])));
                assertNotNull(found);
                assertEquals((int) expected[2], found.getDuration());
                assertEquals((int) expected[1], found.getFluidInputs().getFirst().getAmount());
            }
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
        assertEquals(baseline, map.getRecipeList().size());
    }

    @Test
    void circuitSolderMultiplierSurvivesCopyAndTypedExpansion() {
        var builder = new CircuitAssemblerRecipeBuilder().solderMultiplier(3);
        assertEquals(3, builder.copy().getSolderMultiplier());
        var result = RecipeDefinitionCompiler.prepare(itemDefinition(RecipeMaps.CIRCUIT_ASSEMBLER_RECIPES)
                .withAttributes(new RecipeAttribute.SolderMultiplier(3))).value().orElseThrow();
        assertEquals(2, result.size());
        assertEquals(216, result.getFirst().recipe().getFluidInputs().getFirst().getAmount());
        assertEquals(Materials.SolderingAlloy.getFluid(),
                result.getFirst().recipe().getFluidInputs().getFirst().getInputFluidStack().getFluid());
        assertEquals(432, result.getLast().recipe().getFluidInputs().getFirst().getAmount());
    }

    @Test
    void distillationPreparesTowerAndDividedDistilleryRecipesWithoutPublishing() {
        var map = new RecipeMapBuilder<>("typed_distill_" + IDS.incrementAndGet(),
                new UniversalDistillationRecipeBuilder())
                .fluidInputs(1).fluidOutputs(2).build();
        RecipeDefinition definition = new RecipeDefinition(SOURCE, map.unlocalizedName, List.of(), List.of(),
                List.of(new FluidIngredient(FluidValue.of(Materials.Water.getFluid(1000)), true)),
                List.of(FluidValue.of(Materials.Oxygen.getFluid(500)), FluidValue.of(Materials.Hydrogen.getFluid(250))),
                List.of(), List.of(), ChanceMode.OR, ChanceMode.OR, 100, 32, false);
        int baseline = RecipeMaps.DISTILLERY_RECIPES.getRecipeList().size();
        var compilation = RecipeDefinitionCompiler.prepare(definition);
        assertTrue(compilation.value().isPresent(), () -> compilation.diagnostics().toString());
        var prepared = compilation.value().orElseThrow();
        assertEquals(3, prepared.size());
        assertSame(map, prepared.getLast().map());
        assertEquals(50, prepared.getFirst().recipe().getFluidOutputs().getFirst().amount);
        assertEquals(100, prepared.getFirst().recipe().getFluidInputs().getFirst().getAmount());
        assertEquals(20, prepared.getFirst().recipe().getDuration());
        assertEquals(8, prepared.getFirst().recipe().getEUt());
        assertEquals(baseline, RecipeMaps.DISTILLERY_RECIPES.getRecipeList().size());
        assertEquals(1,
                RecipeDefinitionCompiler
                        .prepare(definition.withAttributes(new RecipeAttribute.DistilleryRecipes(false)))
                        .value().orElseThrow().size());
        var disabled = map.recipeBuilder().disableDistilleryRecipes().copy();
        AtomicInteger generated = new AtomicInteger();
        disabled.expandDistilleryRecipes(ignored -> generated.incrementAndGet());
        assertEquals(0, generated.get());
    }

    @Test
    void nativeDistillationRegistersChildrenThroughBothEntryPoints() {
        var children = RecipeMaps.DISTILLERY_RECIPES;
        var baseline = List.copyOf(children.getRecipeList());
        try {
            for (boolean receiptEntry : List.of(false, true)) {
                var map = new RecipeMapBuilder<>("native_distill_" + IDS.incrementAndGet(),
                        new UniversalDistillationRecipeBuilder()).fluidInputs(1).fluidOutputs(2).build();
                var builder = map.recipeBuilder().fluidInputs(Materials.Water.getFluid(1000))
                        .fluidOutputs(Materials.Oxygen.getFluid(500), Materials.Hydrogen.getFluid(250))
                        .duration(100).EUt(32);
                if (receiptEntry) {
                    var receipt = builder.buildAndRegisterWithReceipt().orElseThrow();
                    assertSame(map, receipt.map());
                    assertSame(receipt.recipe(), map.getRecipeList().iterator().next());
                } else {
                    builder.buildAndRegister();
                }
                assertEquals(1, map.getRecipeList().size());
                var added = children.getRecipeList().stream().filter(recipe -> !baseline.contains(recipe)).toList();
                assertEquals(2, added.size());
                assertTrue(added.stream().allMatch(recipe -> recipe.getEUt() == 8 && recipe.getDuration() == 20));
                added.forEach(recipe -> assertTrue(children.removeRecipe(recipe)));
            }
        } finally {
            List.copyOf(children.getRecipeList()).stream().filter(recipe -> !baseline.contains(recipe))
                    .forEach(children::removeRecipe);
        }
    }

    @Test
    void derivedConflictAndThrownExpansionNeverPublishParentOrChildren() {
        var child = simpleMap();
        var parent = new RecipeMapBuilder<>("typed_expand_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1)
                .onExpand(new ResourceLocation("test", "child"), (builder, derived) -> derived.accept(
                        child.recipeBuilder().inputs(new ItemStack(Items.APPLE)).outputs(new ItemStack(Items.DIAMOND))
                                .duration(1).EUt(1)))
                .build();
        var registrar = new RecipeDefinitionRegistrar();
        child.recipeBuilder().inputs(new ItemStack(Items.APPLE)).outputs(new ItemStack(Items.COAL)).duration(1).EUt(1)
                .buildAndRegister();
        Recipe existing = child.getRecipeList().iterator().next();
        assertTrue(registrar.replace("conflict", List.of(itemDefinition(parent))).value().isEmpty());
        assertTrue(parent.getRecipeList().isEmpty());
        assertEquals(List.of(existing), List.copyOf(child.getRecipeList()));
        parent.removeBuildAction(new ResourceLocation("test", "child"));
        parent.onRecipeBuild(new ResourceLocation("test", "throw"),
                (RecipeExpansion<SimpleRecipeBuilder>) (builder, derived) -> {
                    derived.accept(child.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                            .outputs(new ItemStack(Items.DIAMOND)).duration(1).EUt(1));
                    throw new IllegalArgumentException("rejected expansion");
                });
        var failed = registrar.replace("throw", List.of(itemDefinition(parent)));
        assertEquals("recipe.expansion.failed", failed.diagnostics().getFirst().code());
        assertEquals(SOURCE, failed.diagnostics().getFirst().origin());
        assertTrue(parent.getRecipeList().isEmpty());
        assertEquals(List.of(existing), List.copyOf(child.getRecipeList()));
    }

    @Test
    void arbitraryCallbacksAndExpansionCyclesAreRejected() {
        var map = simpleMap();
        AtomicInteger calls = new AtomicInteger();
        map.onRecipeBuild(new ResourceLocation("test", "unsafe"), ignored -> calls.incrementAndGet());
        assertTrue(RecipeDefinitionCompiler.compile(itemDefinition(map)).value().isEmpty());
        assertEquals(0, calls.get());
        map.removeBuildAction(new ResourceLocation("test", "unsafe"));
        map.onRecipeBuild(new ResourceLocation("test", "cycle"),
                (RecipeExpansion<SimpleRecipeBuilder>) (builder, derived) -> derived.accept(builder.copy()));
        assertEquals("recipe.expansion.cycle",
                RecipeDefinitionCompiler.compile(itemDefinition(map)).diagnostics().getFirst().code());
        assertTrue(map.getRecipeList().isEmpty());
    }

    private static RecipeMap<SimpleRecipeBuilder> simpleMap() {
        return new RecipeMapBuilder<>("typed_expansion_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    private static RecipeDefinition itemDefinition(RecipeMap<?> map) {
        return new RecipeDefinition(SOURCE, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.APPLE))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 100, 32, false);
    }
}

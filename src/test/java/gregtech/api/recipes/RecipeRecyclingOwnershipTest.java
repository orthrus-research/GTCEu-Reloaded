package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecipeAttribute;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRecyclingOwnershipTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void recipeAndRecyclingReplacementRestoreTheOriginalExactEntry() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        RecyclingData original = new RecyclingData(new MaterialStack(Materials.Gold, 77));
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            manager.registerRecyclingData(stack(Items.DIAMOND, 1), original);
            for (int amount : List.of(2, 4, 3)) {
                assertTrue(registrar
                        .replace("composition",
                                List.of(definition(map, "source", Items.APPLE, amount, Items.DIAMOND, 2)))
                        .value().isPresent());
                assertEquals(amount * 50L,
                        manager.getRecyclingData(stack(Items.DIAMOND, 1)).getMaterials().getFirst().amount);
            }
            assertTrue(registrar.clear().value().isPresent());
            assertSame(original, manager.getRecyclingData(stack(Items.DIAMOND, 1)));
            assertTrue(map.getRecipeList().isEmpty());
        } finally {
            restore(before);
        }
    }

    @Test
    void chainedRecipesResolvePendingCompositionWithoutPublishingIntermediateData() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            // Deliberately reverse declaration order: the second recipe supplies the first's composition.
            var result = registrar.replace("chain", List.of(
                    definition(map, "second", Items.DIAMOND, 3, Items.EMERALD, 1),
                    definition(map, "first", Items.APPLE, 2, Items.DIAMOND, 1)));
            assertTrue(result.value().isPresent(), () -> result.diagnostics().toString());
            assertEquals(600, manager.getRecyclingData(stack(Items.EMERALD, 1)).getMaterials().getFirst().amount);
            assertTrue(registrar.clear().value().isPresent());
            assertEquals(before.get(new ItemAndMetadata(stack(Items.DIAMOND, 1))),
                    manager.snapshot().get(new ItemAndMetadata(stack(Items.DIAMOND, 1))));
        } finally {
            restore(before);
        }
    }

    @Test
    void conflictingOrCyclicCompositionAndExternalMutationLeaveRecipeStateUntouched() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            manager.registerRecyclingData(stack(Items.COAL, 1),
                    new RecyclingData(new MaterialStack(Materials.Gold, 100)));
            var conflict = registrar.replace("conflict", List.of(
                    definition(map, "iron", Items.APPLE, 1, Items.DIAMOND, 1),
                    definition(map, "gold", Items.COAL, 1, Items.DIAMOND, 1)));
            assertEquals("recipe.recycling.conflict", conflict.diagnostics().getFirst().code());
            assertTrue(map.getRecipeList().isEmpty());
            var cycle = registrar.replace("cycle", List.of(
                    definition(map, "one", Items.DIAMOND, 1, Items.EMERALD, 1),
                    definition(map, "two", Items.EMERALD, 1, Items.DIAMOND, 1)));
            assertTrue(cycle.value().isEmpty());
            assertTrue(map.getRecipeList().isEmpty());
            assertTrue(registrar.replace("owned", List.of(definition(map, "owned", Items.APPLE, 1, Items.DIAMOND, 1)))
                    .value().isPresent());
            long revision = map.getRecipeRevision();
            var owned = manager.getRecyclingData(stack(Items.DIAMOND, 1));
            var external = new RecyclingData(new MaterialStack(Materials.Copper, 333));
            manager.registerRecyclingData(stack(Items.DIAMOND, 1), external);
            // Replacing over an externally edited entry is still refused and leaves every table untouched.
            assertTrue(registrar.replace("owned", List.of()).value().isEmpty());
            assertEquals(revision, map.getRecipeRevision());
            assertEquals(1, map.getRecipeList().size());
            assertSame(external, manager.getRecyclingData(stack(Items.DIAMOND, 1)));
            // Clear releases the edited entry instead of wedging: the outside value stays, the owned recipe goes.
            assertTrue(registrar.clear().value().isPresent());
            assertTrue(map.getRecipeList().isEmpty());
            assertSame(external, manager.getRecyclingData(stack(Items.DIAMOND, 1)));
            assertNotSame(owned, manager.getRecyclingData(stack(Items.DIAMOND, 1)));
            assertTrue(registrar.clear().value().isPresent());
            assertSame(external, manager.getRecyclingData(stack(Items.DIAMOND, 1)));
        } finally {
            restore(before);
        }
    }

    @Test
    void recyclingValuesOwnTheirListsAndUnknownCompositionIsNotAnEffect() {
        var mutable = new ArrayList<>(List.of(new MaterialStack(Materials.Iron, 100)));
        var value = new RecyclingData(mutable);
        mutable.clear();
        assertEquals(100, value.getMaterials().getFirst().amount);
        assertThrows(UnsupportedOperationException.class, () -> value.getMaterials().clear());
        assertNull(RecyclingHandler.getRecyclingIngredients(
                List.of(ItemIngredient.exact(stack(Items.COMPASS, 1)).compile()), 1,
                ignored -> null));
    }

    @Test
    void replacingAndRemovingUpstreamGroupsRecomputesRetainedDependents() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        RecyclingData baseline = new RecyclingData(new MaterialStack(Materials.Gold, 7));
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            manager.registerRecyclingData(stack(Items.DIAMOND, 1), baseline);
            assertTrue(registrar.replace("upstream",
                    List.of(definition(map, "first", Items.APPLE, 2, Items.DIAMOND, 1))).value().isPresent());
            assertTrue(registrar.replace("downstream",
                    List.of(definition(map, "second", Items.DIAMOND, 3, Items.EMERALD, 1))).value().isPresent());
            assertEquals(600, manager.getRecyclingData(stack(Items.EMERALD, 1)).getMaterials().getFirst().amount);
            Recipe retained = map.getRecipeList().stream()
                    .filter(recipe -> recipe.getOutputs().getFirst().getItem() == Items.EMERALD).findFirst()
                    .orElseThrow();
            assertTrue(registrar.replace("upstream",
                    List.of(definition(map, "first", Items.APPLE, 4, Items.DIAMOND, 1))).value().isPresent());
            assertEquals(1200, manager.getRecyclingData(stack(Items.EMERALD, 1)).getMaterials().getFirst().amount);
            assertTrue(map.getRecipeList().contains(retained));
            assertTrue(registrar.replace("upstream", List.of()).value().isPresent());
            assertSame(baseline, manager.getRecyclingData(stack(Items.DIAMOND, 1)));
            assertEquals(new RecyclingData(new MaterialStack(Materials.Gold, 21)),
                    manager.getRecyclingData(stack(Items.EMERALD, 1)));
            assertTrue(registrar.clear().value().isPresent());
            assertEquals(before.get(new ItemAndMetadata(stack(Items.EMERALD, 1))),
                    manager.snapshot().get(new ItemAndMetadata(stack(Items.EMERALD, 1))));
        } finally {
            restore(before);
        }
    }

    @Test
    void unknownContributionsAreRetainedAndResolveWhenTheirUpstreamGroupArrives() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        try {
            manager.removeRecyclingData(stack(Items.DIAMOND, 1));
            manager.removeRecyclingData(stack(Items.EMERALD, 1));
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            assertTrue(registrar.replace("downstream",
                    List.of(definition(map, "second", Items.DIAMOND, 3, Items.EMERALD, 1))).value().isPresent());
            assertNull(manager.getRecyclingData(stack(Items.EMERALD, 1)));
            assertTrue(registrar.replace("upstream",
                    List.of(definition(map, "first", Items.APPLE, 2, Items.DIAMOND, 1))).value().isPresent());
            assertEquals(600, manager.getRecyclingData(stack(Items.EMERALD, 1)).getMaterials().getFirst().amount);
            assertTrue(registrar.replace("upstream", List.of()).value().isPresent());
            assertNull(manager.getRecyclingData(stack(Items.EMERALD, 1)));
            assertTrue(registrar.clear().value().isPresent());
        } finally {
            restore(before);
        }
    }

    @Test
    void crossGroupCyclesAndExternalDependentEditsRejectWithoutPublishing() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            var upstream = definition(map, "first", Items.APPLE, 2, Items.DIAMOND, 1);
            assertTrue(registrar.replace("upstream", List.of(upstream)).value().isPresent());
            assertTrue(registrar.replace("downstream",
                    List.of(definition(map, "second", Items.DIAMOND, 3, Items.EMERALD, 1))).value().isPresent());
            var snapshot = manager.snapshot();
            var recipeRevision = map.getRecipeRevision();
            var cycle = registrar.replace("upstream",
                    List.of(definition(map, "cycle", Items.EMERALD, 1, Items.DIAMOND, 1)));
            assertTrue(cycle.value().isEmpty());
            assertEquals("cycle", cycle.diagnostics().getFirst().origin().declaration());
            assertEquals(snapshot, manager.snapshot());
            assertEquals(recipeRevision, map.getRecipeRevision());
            manager.registerRecyclingData(stack(Items.EMERALD, 1),
                    new RecyclingData(new MaterialStack(Materials.Copper, 5)));
            var external = manager.snapshot();
            assertTrue(registrar.replace("upstream", List.of(upstream)).value().isEmpty());
            assertEquals(external, manager.snapshot());
            assertEquals(recipeRevision, map.getRecipeRevision());
            manager.registerRecyclingData(stack(Items.EMERALD, 1),
                    snapshot.get(new ItemAndMetadata(stack(Items.EMERALD, 1))));
            assertTrue(registrar.clear().value().isPresent());
        } finally {
            restore(before);
        }
    }

    @Test
    void exactArithmeticAllowsLargeIntermediateValuesAndRejectsUnrepresentableResultsWithSource() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, Long.MAX_VALUE)));
            assertTrue(registrar.replace("large",
                    List.of(definition(map, "fits", Items.APPLE, 2, Items.DIAMOND, 2))).value().isPresent());
            assertEquals(Long.MAX_VALUE,
                    manager.getRecyclingData(stack(Items.DIAMOND, 1)).getMaterials().getFirst().amount);
            var snapshot = manager.snapshot();
            var revision = map.getRecipeRevision();
            var invalid = registrar.replace("large", List.of(
                    definition(map, "valid-first", Items.COAL, 1, Items.EMERALD, 1),
                    definition(map, "overflow-second", Items.APPLE, 2, Items.DIAMOND, 1)));
            assertTrue(invalid.value().isEmpty());
            assertEquals("overflow-second", invalid.diagnostics().getFirst().origin().declaration());
            assertEquals(snapshot, manager.snapshot());
            assertEquals(revision, map.getRecipeRevision());
            assertTrue(registrar.clear().value().isPresent());
        } finally {
            restore(before);
        }
    }

    @Test
    void contributionsSnapshotQuantitiesAndDoNotRetainMutableRecipeInputs() {
        var stack = stack(Items.APPLE, 2);
        var input = ItemIngredient.exact(stack).compile();
        var output = stack(Items.DIAMOND, 3);
        var origin = new SourceOrigin("java", "composition", 1, 1, "copied", "recipes");
        var contribution = RecyclingContribution.of(origin, output, List.of(input));
        stack.setCount(40);
        output.setCount(50);
        assertEquals(3, contribution.outputCount());
        assertEquals(2L, contribution.inputs().get(new ItemAndMetadata(Items.APPLE, 0)));
        assertThrows(UnsupportedOperationException.class, () -> contribution.inputs().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new RecyclingContribution(origin, contribution.output(), 0, contribution.inputs()));
        var inputs = List.of(ItemIngredient.exact(stack(Items.APPLE, 2)).compile(),
                ItemIngredient.exact(stack(Items.COAL, 2)).compile());
        var combined = RecyclingHandler.getRecyclingIngredients(inputs, 2,
                ignored -> new RecyclingData(new MaterialStack(Materials.Iron, Long.MAX_VALUE / 2)));
        assertEquals(Long.MAX_VALUE - 1, combined.getMaterials().getFirst().amount);
    }

    @Test
    void retainedGroupConflictsReportTheResponsibleRetainedDeclaration() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        try {
            var iron = new RecyclingData(new MaterialStack(Materials.Iron, 100));
            manager.registerRecyclingData(stack(Items.APPLE, 1), iron);
            manager.registerRecyclingData(stack(Items.COAL, 1), iron);
            assertTrue(registrar.replace("upstream",
                    List.of(definition(map, "first", Items.APPLE, 1, Items.DIAMOND, 1))).value().isPresent());
            assertTrue(registrar.replace("downstream", List.of(
                    definition(map, "diamond-route", Items.DIAMOND, 1, Items.EMERALD, 1),
                    definition(map, "coal-route", Items.COAL, 1, Items.EMERALD, 1))).value().isPresent());
            var snapshot = manager.snapshot();
            var revision = map.getRecipeRevision();
            var rejected = registrar.replace("upstream",
                    List.of(definition(map, "first", Items.APPLE, 2, Items.DIAMOND, 1)));
            assertTrue(rejected.value().isEmpty());
            assertEquals("coal-route", rejected.diagnostics().getFirst().origin().declaration());
            assertEquals(snapshot, manager.snapshot());
            assertEquals(revision, map.getRecipeRevision());
            assertTrue(registrar.clear().value().isPresent());
        } finally {
            restore(before);
        }
    }

    @Test
    void staticGroovyUsesTheSameContributionAndShapedArithmeticDoesNotOverflow() {
        Object authored = new GroovyShell().evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.definition.SourceOrigin
                import gregtech.api.recipes.RecyclingContribution
                import gregtech.api.unification.stack.ItemAndMetadata
                import net.minecraft.init.Items
                @CompileStatic
                class RecyclingAuthoring {
                    static RecyclingContribution make() {
                        new RecyclingContribution(new SourceOrigin('groovy', 'composition.groovy', 5, 1,
                            'recipe', 'recipes'), new ItemAndMetadata(Items.DIAMOND, 0), 2,
                            Map.of(new ItemAndMetadata(Items.APPLE, 0), 2L))
                    }
                }
                RecyclingAuthoring.make()
                """);
        var contribution = new RecyclingContribution(
                new SourceOrigin("groovy", "composition.groovy", 5, 1, "recipe", "recipes"),
                new ItemAndMetadata(Items.DIAMOND, 0), 2, Map.of(new ItemAndMetadata(Items.APPLE, 0), 2L));
        assertEquals(contribution, authored);
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, Long.MAX_VALUE)));
            var result = RecyclingHandler.getRecyclingIngredients(2, "AA", 'A', Items.APPLE);
            assertEquals(Long.MAX_VALUE, result.getMaterials().getFirst().amount);
            assertThrows(ArithmeticException.class,
                    () -> RecyclingHandler.getRecyclingIngredients(1, "AA", 'A', Items.APPLE));
            assertNull(RecyclingHandler.getRecyclingIngredients(1, new Object[0]));
            assertThrows(IllegalArgumentException.class,
                    () -> RecyclingHandler.getRecyclingIngredients(0, "A", 'A', Items.APPLE));
        } finally {
            restore(before);
        }
    }

    @Test
    void exactEntriesTakePrecedenceOverPendingWildcardContributions() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var ownership = new RecipeRecyclingOwnership();
        var map = map();
        try {
            manager.registerRecyclingData(stack(Items.APPLE, 1),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            var wildcard = new ItemStack(Items.DIAMOND, 1, gregtech.api.GTValues.W);
            // The wildcard depends on the downstream recipe. The downstream recipe reads the exact output,
            // so it must not acquire a false edge back to the pending wildcard.
            ownership.prepare("wildcard", List.of(
                    owned(map, "wildcard", stack(Items.EMERALD, 1), wildcard),
                    owned(map, "dependent", stack(Items.DIAMOND, 1), stack(Items.EMERALD, 1)),
                    owned(map, "exact", stack(Items.APPLE, 1), stack(Items.DIAMOND, 1))), false, List.of(), List.of())
                    .publish();
            assertEquals(100, manager.getRecyclingData(stack(Items.EMERALD, 1)).getMaterials().getFirst().amount);
            assertEquals(100, manager.getRecyclingData(wildcard).getMaterials().getFirst().amount);
            ownership.prepare("all", List.of(), true, List.of(), List.of()).publish();

            manager.registerRecyclingData(stack(Items.DIAMOND, 1),
                    new RecyclingData(new MaterialStack(Materials.Gold, 77)));
            ownership.prepare("wildcard", List.of(
                    owned(map, "from-base-exact", stack(Items.DIAMOND, 1), wildcard)), false, List.of(), List.of())
                    .publish();
            assertEquals(77, manager.getRecyclingData(wildcard).getMaterials().getFirst().amount);
            ownership.prepare("all", List.of(), true, List.of(), List.of()).publish();
        } finally {
            restore(before);
        }
    }

    @Test
    void aLongReverseDeclaredChainResolvesAndRestoresEveryOutput() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var ownership = new RecipeRecyclingOwnership();
        var map = map();
        try {
            for (int i = 0; i <= 128; i++) manager.removeRecyclingData(new ItemStack(Items.DYE, 1, i));
            manager.registerRecyclingData(new ItemStack(Items.DYE, 1, 0),
                    new RecyclingData(new MaterialStack(Materials.Iron, 100)));
            var chain = new ArrayList<OwnedRecipeDefinition>();
            for (int i = 128; i >= 1; i--) {
                chain.add(owned(map, "step-" + i,
                        new ItemStack(Items.DYE, 1, i - 1), new ItemStack(Items.DYE, 1, i)));
            }
            var prepared = ownership.prepare("chain", chain, false, List.of(), List.of());
            assertNull(manager.getRecyclingData(new ItemStack(Items.DYE, 1, 128)));
            prepared.publish();
            for (int i = 1; i <= 128; i++) {
                assertEquals(100, manager.getRecyclingData(new ItemStack(Items.DYE, 1, i))
                        .getMaterials().getFirst().amount);
            }
            ownership.prepare("all", List.of(), true, List.of(), List.of()).publish();
            for (int i = 1; i <= 128; i++) assertNull(manager.getRecyclingData(new ItemStack(Items.DYE, 1, i)));
        } finally {
            restore(before);
        }
    }

    @Test
    void unspecifiedPrefixQuantityIsNotNegativeExplicitComposition() {
        var prefix = new gregtech.api.unification.ore.OrePrefix("recyclingUnknownQuantityFixture", -1,
                null, null, 0, null);
        var item = new Item().setRegistryName("gregtech", "recycling_unknown_quantity_fixture");
        var stack = new ItemStack(item);
        gregtech.api.unification.OreDictUnifier.onItemRegistration(
                new net.minecraftforge.oredict.OreDictionary.OreRegisterEvent(prefix.name() + "Wood", stack));
        assertEquals(-1, gregtech.api.unification.OreDictUnifier.getMaterial(stack).amount);
        var input = ItemIngredient.exact(stack).compile();
        assertNull(RecyclingHandler.getRecyclingIngredients(List.of(input), 1, ignored -> null));
        assertThrows(ArithmeticException.class, () -> RecyclingHandler.getRecyclingIngredients(List.of(input), 1,
                ignored -> new RecyclingData(new MaterialStack(Materials.Wood, -1))));
    }

    @Test
    void nativeResolverCallbackReceivesTheOriginalInputNbt() {
        var first = stack(Items.APPLE, 2);
        first.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        first.getTagCompound().setLong("composition", 5);
        var second = stack(Items.APPLE, 3);
        second.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        second.getTagCompound().setLong("composition", 7);
        var composed = RecyclingHandler.getRecyclingIngredients(List.of(
                ItemIngredient.exact(first).compile(), ItemIngredient.exact(second).compile()), 1,
                stack -> new RecyclingData(new MaterialStack(Materials.Iron,
                        stack.getTagCompound().getLong("composition"))));
        assertEquals(31, composed.getMaterials().getFirst().amount);
    }

    private static OwnedRecipeDefinition owned(RecipeMap<SimpleRecipeBuilder> map, String name, ItemStack input,
                                               ItemStack output) {
        var definition = new RecipeDefinition(new SourceOrigin("java", "graph", 1, 1, name, "recipes"),
                map.unlocalizedName, List.of(ItemIngredient.exact(input)), List.of(ItemValue.of(output)), 40, 8, false);
        Recipe recipe = map.recipeBuilder().inputs(input).outputs(output).duration(40).EUt(8).build().getResult();
        return new OwnedRecipeDefinition(map, recipe, definition.origin(), true, List.of());
    }

    private static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("typed_recycling_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static RecipeDefinition definition(RecipeMap<?> map, String name, Item input, int count, Item output,
                                               int outputCount) {
        return new RecipeDefinition(new SourceOrigin("java", "RecyclingRecipes.java", 20, 1, name, "recipes"),
                map.unlocalizedName,
                List.of(ItemIngredient.exact(stack(input, count))), List.of(ItemValue.of(stack(output, outputCount))),
                40, 8, false)
                .withAttributes(new RecipeAttribute.Recycling());
    }

    private static void restore(Map<ItemAndMetadata, RecyclingData> before) {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var replacement = new java.util.HashMap<ItemAndMetadata, RecyclingData>();
        manager.snapshot().keySet().forEach(key -> replacement.put(key, null));
        replacement.putAll(before);
        manager.prepare(manager.revision(), replacement).publish();
    }
}

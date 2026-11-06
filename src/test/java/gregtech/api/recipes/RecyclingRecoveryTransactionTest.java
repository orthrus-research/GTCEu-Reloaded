package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.*;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecyclingRecoveryTransactionTest {

    private static final AtomicInteger IDS = new AtomicInteger(2000);
    private static RecyclingUnificationFixture unification;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.perform();
        unification = new RecyclingUnificationFixture();
    }

    @AfterAll
    static void restore() throws Exception {
        unification.close();
    }

    @Test
    void compositionAndRecoveryPublishTogetherAndRestoreTheirExactBaselines() {
        var manager = new RecyclingManager();
        var owner = new RecyclingOwner();
        var input = input();
        var key = new ItemAndMetadata(input);
        var baselineData = new RecyclingData(new MaterialStack(Materials.Copper, GTValues.M));
        manager.registerRecyclingData(key, baselineData);
        var baseline = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(input).outputs(new ItemStack(Items.COAL))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        var graph = manager.ownership();
        var policy = policy(input, false);
        try {
            var prepared = graph.prepareRecovery(owner, "recovery", List.of(fixed(input, 2 * GTValues.M)),
                    List.of(policy), List.of(baseline), false);
            assertSame(baselineData, manager.getRecyclingData(key));
            assertSame(baseline.recipe(), find(input));
            assertEquals(2 * GTValues.M, prepared.resolved().get(key).getMaterials().getFirst().amount);
            assertThrows(UnsupportedOperationException.class, () -> prepared.resolved().clear());
            prepared.publish();
            assertThrows(IllegalStateException.class, prepared::publish);
            assertEquals(2, find(input).getOutputs().getFirst().getCount());
            assertEquals(2 * GTValues.M, manager.getRecyclingData(key).getMaterials().getFirst().amount);
            var previous = find(input);
            assertThrows(RecipeRegistrationException.class, () -> graph.prepareRecovery(owner, "recovery",
                    List.of(fixed(input, Long.MAX_VALUE)), List.of(policy), List.of(), false));
            assertSame(previous, find(input));
            assertEquals(2 * GTValues.M, manager.getRecyclingData(key).getMaterials().getFirst().amount);
            graph.prepareRecovery(owner, "recovery", List.of(fixed(input, 0)), List.of(policy), List.of(), false)
                    .publish();
            assertTrue(manager.getRecyclingData(key).getMaterials().isEmpty());
            assertNull(find(input));
            graph.prepareRecovery(owner, "recovery", List.of(fixed(input, GTValues.M)), List.of(policy), List.of(),
                    false)
                    .publish();
            assertEquals(1, find(input).getOutputs().getFirst().getCount());
            graph.prepare(owner, "all", List.of(), true).publish();
            assertSame(baseline.recipe(), find(input));
            assertSame(baselineData, manager.getRecyclingData(key));
        } finally {
            graph.prepare(owner, "all", List.of(), true).publish();
            baseline.map().removeRecipe(baseline.recipe());
        }
    }

    @Test
    void dependentRecoveryAndDirectRecipeChangesShareTheSameMapPublication() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var raw = input();
        var intermediate = input();
        var recovered = input();
        var downstream = new RecyclingOwner();
        var registrar = new RecipeDefinitionRegistrar();
        try {
            manager.registerRecyclingData(raw, new RecyclingData(new MaterialStack(Materials.Iron, GTValues.M)));
            assertTrue(registrar.replace("upstream", List.of(producer(raw, intermediate, 1))).value().isPresent());
            var route = new RecyclingContribution(origin(), new ItemAndMetadata(recovered), 1,
                    Map.of(new ItemAndMetadata(intermediate), 2L), Map.of(), RecyclingRoutePolicy.MINIMUM_INPUTS);
            manager.ownership().prepareRecovery(downstream, "downstream", List.of(route),
                    List.of(policy(recovered, false)), List.of(), false).publish();
            assertEquals(2, find(recovered).getOutputs().getFirst().getCount());
            assertTrue(registrar.replace("upstream", List.of(producer(raw, intermediate, 2))).value().isPresent());
            assertEquals(4, find(recovered).getOutputs().getFirst().getCount());
            assertEquals(4 * GTValues.M, manager.getRecyclingData(recovered).getMaterials().getFirst().amount);
            var twoRaw = raw.copy();
            twoRaw.setCount(2);
            assertNotNull(find(twoRaw), "Direct recipe must survive the dependent index update in the same map");
            assertNull(find(raw));
            assertTrue(registrar.clear().value().isPresent());
            assertNull(find(twoRaw));
            assertNull(find(recovered));
            assertTrue(manager.getRecyclingData(recovered).getMaterials().isEmpty());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            manager.ownership().prepare(downstream, "all", List.of(), true).publish();
            var replacement = new HashMap<ItemAndMetadata, RecyclingData>();
            manager.snapshot().keySet().forEach(key -> replacement.put(key, null));
            replacement.putAll(before);
            manager.prepare(manager.revision(), replacement).publish();
        }
    }

    @Test
    void lateMapConflictAndStalePreparationPublishNeitherMetadataNorOtherMaps() {
        var manager = new RecyclingManager();
        var graph = manager.ownership();
        var owner = new RecyclingOwner();
        var input = input();
        var policy = policy(input, true);
        var blocker = RecipeMaps.ARC_FURNACE_RECIPES.recipeBuilder().inputs(input).outputs(new ItemStack(Items.COAL))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        try {
            assertThrows(RecipeRegistrationException.class, () -> graph.prepareRecovery(owner, "recovery",
                    List.of(fixed(input, GTValues.M)), List.of(policy), List.of(), false));
            assertNull(manager.getRecyclingData(input));
            assertNull(find(input));
            assertNull(RecipeMaps.EXTRACTOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of()));
            blocker.map().removeRecipe(blocker.recipe());
            var pending = graph.prepareRecovery(owner, "recovery", List.of(fixed(input, GTValues.M)),
                    List.of(policy), List.of(), false);
            var unrelated = input();
            var outside = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(unrelated)
                    .outputs(new ItemStack(Items.COAL))
                    .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
            try {
                assertFalse(pending.isCurrent());
                assertThrows(IllegalStateException.class, pending::publish);
                assertNull(manager.getRecyclingData(input));
                assertNull(find(input));
                assertSame(outside.recipe(), find(unrelated));
            } finally {
                outside.map().removeRecipe(outside.recipe());
            }
        } finally {
            blocker.map().removeRecipe(blocker.recipe());
            graph.prepare(owner, "all", List.of(), true).publish();
        }
    }

    @Test
    void evenEmptyPreparedChangesAreRevisionCheckedAndSingleUse() {
        var manager = new RecyclingManager();
        var owner = new RecyclingOwner();
        var pending = manager.ownership().prepare(owner, "none", List.of(), false);
        manager.registerRecyclingData(input(), new RecyclingData(new MaterialStack(Materials.Iron, GTValues.M)));
        assertFalse(pending.isCurrent());
        assertThrows(IllegalStateException.class, pending::publish);
        var empty = manager.ownership().prepare(owner, "none", List.of(), false);
        empty.publish();
        assertThrows(IllegalStateException.class, empty::publish);
    }

    private static RecipeDefinition producer(ItemStack input, ItemStack output, int count) {
        var stack = input.copy();
        stack.setCount(count);
        return new RecipeDefinition(origin(), RecipeMaps.MACERATOR_RECIPES.unlocalizedName,
                List.of(ItemIngredient.exact(stack)), List.of(ItemValue.of(output)), 20, 8, false)
                .withAttributes(new RecipeAttribute.Recycling());
    }

    @Test
    void registrarClearRestoresCompositionAndRecipesAtomicallyAcrossBothGroupKinds() {
        var input = input();
        var directInput = input();
        var replacementInput = input();
        var baseline = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(input).outputs(new ItemStack(Items.COAL))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var original = manager.snapshot().get(new ItemAndMetadata(replacementInput));
        var registrar = new RecipeDefinitionRegistrar();
        NativeRecipeRegistration blocker = null;
        try {
            var direct = new RecipeDefinition(origin(), RecipeMaps.MACERATOR_RECIPES.unlocalizedName,
                    List.of(ItemIngredient.exact(directInput)), List.of(ItemValue.of(new ItemStack(Items.COAL))),
                    20, 8, false);
            assertTrue(registrar.replace("direct", List.of(direct)).value().isPresent());
            assertEquals(1, registrar.replaceRecovery("derived", List.of(fixed(replacementInput, GTValues.M)),
                    List.of(policy(replacementInput, false)), List.of(baseline)).value().orElseThrow().recipes());
            assertEquals("recipe.group.owner", registrar.replaceRecycling("derived", List.of())
                    .diagnostics().getFirst().code());
            assertEquals("recipe.group.owner", registrar.replaceRecovery("direct", List.of(), List.of(), List.of())
                    .diagnostics().getFirst().code());
            var current = find(replacementInput);
            var directCurrent = find(directInput);
            blocker = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(input).outputs(new ItemStack(Items.DIAMOND))
                    .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
            assertTrue(registrar.clear().value().isEmpty());
            assertSame(current, find(replacementInput));
            assertSame(directCurrent, find(directInput));
            assertEquals(GTValues.M, manager.getRecyclingData(replacementInput).getMaterials().getFirst().amount);
            blocker.map().removeRecipe(blocker.recipe());
            assertEquals(2, registrar.clear().value().orElseThrow());
            assertNull(find(replacementInput));
            assertNull(find(directInput));
            assertSame(baseline.recipe(), find(input));
            assertEquals(original, manager.snapshot().get(new ItemAndMetadata(replacementInput)));
        } finally {
            if (blocker != null) blocker.map().removeRecipe(blocker.recipe());
            assertTrue(registrar.clear().value().isPresent());
            baseline.map().removeRecipe(baseline.recipe());
        }
    }

    private static RecyclingContribution fixed(ItemStack output, long amount) {
        return new RecyclingContribution(origin(), new ItemAndMetadata(output), 1, Map.of(),
                amount == 0 ? Map.of() : Map.of(Materials.Iron, amount), RecyclingRoutePolicy.REPLACE);
    }

    private static RecyclingRecoveryDefinition policy(ItemStack input, boolean allMaps) {
        return new RecyclingRecoveryDefinition(origin(), ItemValue.of(input), !allMaps,
                allMaps ? OrePrefix.toolHeadDrill : null);
    }

    private static ItemStack input() {
        return new ItemStack(Items.DYE, 1, IDS.incrementAndGet());
    }

    private static Recipe find(ItemStack input) {
        return RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of());
    }

    private static SourceOrigin origin() {
        return new SourceOrigin("java", "RecyclingRecoveryTransactionTest", 1, 1, "recovery", "postInit");
    }
}

package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecipeAttribute;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SharedRecyclingOwnershipTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void separateRegistrarsShareDependencyResolutionAndClearOnlyTheirOwnGroups() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var first = new RecipeDefinitionRegistrar();
        var second = new RecipeDefinitionRegistrar();
        var map = new RecipeMapBuilder<>("shared_recycling_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        try {
            manager.registerRecyclingData(key(Items.APPLE), data(100));
            manager.registerRecyclingData(key(Items.DIAMOND), data(7));
            assertTrue(first.replace("same-name", List.of(recipe(map, "upstream", Items.APPLE, 2, Items.DIAMOND)))
                    .value().isPresent());
            assertTrue(second.replace("same-name", List.of(recipe(map, "downstream", Items.DIAMOND, 3, Items.EMERALD)))
                    .value().isPresent());
            assertEquals(data(600), manager.getRecyclingData(key(Items.EMERALD)));
            assertTrue(first.replace("same-name", List.of(recipe(map, "upstream", Items.APPLE, 4, Items.DIAMOND)))
                    .value().isPresent());
            assertEquals(data(1200), manager.getRecyclingData(key(Items.EMERALD)));
            assertTrue(first.clear().value().isPresent());
            assertEquals(data(21), manager.getRecyclingData(key(Items.EMERALD)));
            assertEquals(List.of("same-name"), second.groups());
            assertEquals(1, map.getRecipeList().size());
            assertTrue(second.clear().value().isPresent());
            assertEquals(before.get(key(Items.EMERALD)), manager.snapshot().get(key(Items.EMERALD)));
        } finally {
            first.clear();
            second.clear();
            restore(manager, before);
        }
    }

    @Test
    void separateRegistrarsCannotHideCrossOwnerCyclesOrClaimAnOwnedOutput() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var first = new RecipeDefinitionRegistrar();
        var second = new RecipeDefinitionRegistrar();
        var map = new RecipeMapBuilder<>("shared_cycle_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        try {
            manager.registerRecyclingData(key(Items.APPLE), data(100));
            assertTrue(first.replace("upstream", List.of(recipe(map, "first", Items.APPLE, 1, Items.DIAMOND)))
                    .value().isPresent());
            assertTrue(second.replace("downstream", List.of(recipe(map, "second", Items.DIAMOND, 1, Items.EMERALD)))
                    .value().isPresent());
            var stable = manager.snapshot();
            long revision = map.getRecipeRevision();
            var cycle = first.replace("upstream", List.of(recipe(map, "cycle", Items.EMERALD, 1, Items.DIAMOND)));
            assertTrue(cycle.value().isEmpty());
            assertEquals("recipe.recycling.conflict", cycle.diagnostics().getFirst().code());
            var conflict = second.replace("duplicate", List.of(recipe(map, "duplicate", Items.COAL, 1, Items.DIAMOND)));
            assertTrue(conflict.value().isEmpty());
            assertEquals("duplicate", conflict.diagnostics().getFirst().origin().declaration());
            assertEquals(stable, manager.snapshot());
            assertEquals(revision, map.getRecipeRevision());
        } finally {
            first.clear();
            second.clear();
            restore(manager, before);
        }
    }

    @Test
    void nativeOwnersCanPrepareWithoutPublishingAndStaleChangesCannotEraseOtherOwners() {
        var manager = new RecyclingManager();
        var graph = manager.ownership();
        var first = new RecyclingOwner();
        var second = new RecyclingOwner();
        manager.registerRecyclingData(key(Items.APPLE), data(100));
        var firstChange = graph.prepare(first, "same", List.of(contribution("first", Items.APPLE, 2, Items.DIAMOND)),
                false);
        var staleEmpty = graph.prepare(second, "empty", List.of(), true);
        var staleReal = graph.prepare(second, "same", List.of(contribution("second", Items.DIAMOND, 3, Items.EMERALD)),
                false);
        assertNull(manager.getRecyclingData(key(Items.DIAMOND)));
        firstChange.publish();
        assertFalse(staleEmpty.isCurrent());
        assertFalse(staleReal.isCurrent());
        assertThrows(IllegalStateException.class, staleEmpty::publish);
        assertThrows(IllegalStateException.class, staleReal::publish);
        assertEquals(data(200), manager.getRecyclingData(key(Items.DIAMOND)));
        graph.prepare(second, "same", List.of(contribution("second", Items.DIAMOND, 3, Items.EMERALD)), false)
                .publish();
        assertEquals(data(600), manager.getRecyclingData(key(Items.EMERALD)));
        graph.prepare(first, "all", List.of(), true).publish();
        assertNull(manager.getRecyclingData(key(Items.DIAMOND)));
        assertNull(manager.getRecyclingData(key(Items.EMERALD)));
        graph.prepare(first, "same", List.of(contribution("first", Items.APPLE, 4, Items.DIAMOND)), false).publish();
        assertEquals(data(1200), manager.getRecyclingData(key(Items.EMERALD)));
        graph.prepare(second, "all", List.of(), true).publish();
        assertEquals(data(400), manager.getRecyclingData(key(Items.DIAMOND)));
        assertNull(manager.getRecyclingData(key(Items.EMERALD)));
    }

    @Test
    void nativeRegistryChangesInvalidatePreparedGraphPublication() {
        var manager = new RecyclingManager();
        var owner = new RecyclingOwner();
        manager.registerRecyclingData(key(Items.APPLE), data(100));
        var prepared = manager.ownership().prepare(owner, "first",
                List.of(contribution("first", Items.APPLE, 2, Items.DIAMOND)), false);
        manager.registerRecyclingData(key(Items.APPLE), data(200));
        assertFalse(prepared.isCurrent());
        assertThrows(IllegalStateException.class, prepared::publish);
        assertNull(manager.getRecyclingData(key(Items.DIAMOND)));
        manager.ownership()
                .prepare(owner, "first", List.of(contribution("first", Items.APPLE, 2, Items.DIAMOND)), false)
                .publish();
        assertEquals(data(400), manager.getRecyclingData(key(Items.DIAMOND)));
    }

    private static RecyclingContribution contribution(String name, Item input, int count, Item output) {
        return new RecyclingContribution(origin(name), key(output), 1, Map.of(key(input), (long) count));
    }

    private static RecipeDefinition recipe(RecipeMap<?> map, String name, Item input, int count, Item output) {
        return new RecipeDefinition(origin(name), map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(input, count))),
                List.of(ItemValue.of(new ItemStack(output))),
                40, 8, false).withAttributes(new RecipeAttribute.Recycling());
    }

    private static SourceOrigin origin(String name) {
        return new SourceOrigin("java", "SharedRecyclingOwnershipTest.java", 1, 1, name, "recipes");
    }

    private static ItemAndMetadata key(Item item) {
        return new ItemAndMetadata(item, 0);
    }

    private static RecyclingData data(long amount) {
        return new RecyclingData(new MaterialStack(Materials.Iron, amount));
    }

    private static void restore(RecyclingManager manager, Map<ItemAndMetadata, RecyclingData> before) {
        var replacement = new HashMap<ItemAndMetadata, RecyclingData>();
        manager.snapshot().keySet().forEach(key -> replacement.put(key, null));
        replacement.putAll(before);
        manager.prepare(manager.revision(), replacement).publish();
    }
}

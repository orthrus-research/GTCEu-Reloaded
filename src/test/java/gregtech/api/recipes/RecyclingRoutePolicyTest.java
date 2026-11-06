package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecyclingRoutePolicyTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void minimumIntersectsIngredientIdentitiesAndUsesExactPerOutputFractions() {
        var manager = new RecyclingManager();
        manager.registerRecyclingData(key(Items.APPLE), data(Materials.Iron, 100));
        manager.registerRecyclingData(key(Items.COAL), data(Materials.Copper, 500));
        var first = contribution("first", 3, Map.of(key(Items.APPLE), 1L, key(Items.COAL), 2L),
                Map.of(Materials.Gold, 100L), RecyclingRoutePolicy.MINIMUM_INPUTS);
        var second = contribution("second", 5, Map.of(key(Items.APPLE), 2L),
                Map.of(Materials.Gold, 200L), RecyclingRoutePolicy.MINIMUM_INPUTS);
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(first, second), false).publish();
        assertEquals(List.of(new MaterialStack(Materials.Gold, 33), new MaterialStack(Materials.Iron, 33)),
                manager.getRecyclingData(key(Items.DIAMOND)).getMaterials());
    }

    @Test
    void disjointItemsDoNotIntersectMerelyBecauseTheirMaterialsMatch() {
        var manager = new RecyclingManager();
        var owner = new RecyclingOwner();
        manager.registerRecyclingData(key(Items.APPLE), data(Materials.Iron, 100));
        manager.registerRecyclingData(key(Items.COAL), data(Materials.Iron, 100));
        RecyclingData baseline = data(Materials.Gold, 700);
        manager.registerRecyclingData(key(Items.DIAMOND).toWildcard(), baseline);
        var first = contribution("first", 1, Map.of(key(Items.APPLE), 1L), Map.of(),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        var second = contribution("second", 1, Map.of(key(Items.COAL), 1L), Map.of(),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        var downstream = new RecyclingContribution(origin("downstream"), key(Items.EMERALD), 1,
                Map.of(key(Items.DIAMOND), 3L));
        manager.ownership().prepare(owner, "routes", List.of(first, second, downstream), false).publish();
        assertTrue(manager.getRecyclingData(key(Items.DIAMOND)).getMaterials().isEmpty());
        assertTrue(manager.getRecyclingData(key(Items.EMERALD)).getMaterials().isEmpty());
        manager.ownership().prepare(owner, "routes", List.of(), true).publish();
        assertSame(baseline, manager.getRecyclingData(key(Items.DIAMOND)));
        assertNull(manager.getRecyclingData(key(Items.EMERALD)));
    }

    @Test
    void replacementDiscardsPriorDependenciesBeforeCycleDetection() {
        var manager = new RecyclingManager();
        var self = contribution("obsolete-cycle", 1, Map.of(key(Items.DIAMOND), 1L), Map.of(),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        var replacement = contribution("forced", 1, Map.of(), Map.of(Materials.Iron, 200L),
                RecyclingRoutePolicy.REPLACE);
        var later = contribution("later", 2, Map.of(), Map.of(Materials.Iron, 300L),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(self, replacement, later), false).publish();
        assertEquals(data(Materials.Iron, 150), manager.getRecyclingData(key(Items.DIAMOND)));
    }

    @Test
    void directMaterialsAndItemCompositionSumExactlyBeforeFinalRounding() {
        var manager = new RecyclingManager();
        var owner = new RecyclingOwner();
        manager.registerRecyclingData(key(Items.APPLE), data(Materials.Iron, Long.MAX_VALUE));
        var mixed = contribution("mixed", 2, Map.of(key(Items.APPLE), 1L), Map.of(Materials.Iron, Long.MAX_VALUE),
                RecyclingRoutePolicy.CONSISTENT);
        manager.ownership().prepare(owner, "routes", List.of(mixed), false).publish();
        assertEquals(data(Materials.Iron, Long.MAX_VALUE), manager.getRecyclingData(key(Items.DIAMOND)));
        manager.registerRecyclingData(key(Items.APPLE),
                new RecyclingData(new MaterialStack(Materials.Iron, Long.MAX_VALUE),
                        new MaterialStack(Materials.Iron, Long.MAX_VALUE)));
        var perItemOverflow = contribution("large-item", 2, Map.of(key(Items.APPLE), 1L), Map.of(),
                RecyclingRoutePolicy.CONSISTENT);
        manager.ownership().prepare(owner, "routes", List.of(perItemOverflow), false).publish();
        assertEquals(data(Materials.Iron, Long.MAX_VALUE), manager.getRecyclingData(key(Items.DIAMOND)));
    }

    @Test
    void overflowAndMixedPoliciesRejectBeforePublicationWithTheirSource() {
        var manager = new RecyclingManager();
        var owner = new RecyclingOwner();
        var valid = contribution("valid", 1, Map.of(), Map.of(Materials.Iron, 20L), RecyclingRoutePolicy.CONSISTENT);
        manager.ownership().prepare(owner, "routes", List.of(valid), false).publish();
        manager.registerRecyclingData(key(Items.APPLE), data(Materials.Iron, Long.MAX_VALUE));
        var before = manager.snapshot();
        long revision = manager.revision();
        var overflowing = contribution("overflow", 1, Map.of(key(Items.APPLE), 2L), Map.of(),
                RecyclingRoutePolicy.CONSISTENT);
        var failure = assertThrows(RecyclingOwnershipException.class,
                () -> manager.ownership().prepare(owner, "routes", List.of(overflowing), false));
        assertEquals(origin("overflow"), failure.origin());
        var minimum = contribution("mixed", 1, Map.of(), Map.of(Materials.Iron, 10L),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        failure = assertThrows(RecyclingOwnershipException.class,
                () -> manager.ownership().prepare(owner, "routes", List.of(valid, minimum), false));
        assertEquals(origin("mixed"), failure.origin());
        assertEquals(before, manager.snapshot());
        assertEquals(revision, manager.revision());
    }

    @Test
    void minimumFractionsAreComparedWithoutLongCrossProductOverflow() {
        var manager = new RecyclingManager();
        var first = contribution("first", Integer.MAX_VALUE, Map.of(), Map.of(Materials.Iron, Long.MAX_VALUE),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        var second = contribution("second", Integer.MAX_VALUE - 1, Map.of(), Map.of(Materials.Iron, Long.MAX_VALUE - 1),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(first, second), false).publish();
        assertEquals(data(Materials.Iron, Long.MAX_VALUE / Integer.MAX_VALUE),
                manager.getRecyclingData(key(Items.DIAMOND)));
    }

    @Test
    void materialsAreSnapshottedAndUseMaterialIdentity() {
        var materials = new HashMap<Material, Long>();
        materials.put(Materials.Iron, 5_000_000_000L);
        materials.put(Materials.Gold, 7L);
        var declaration = contribution("materials", 1, Map.of(), materials, RecyclingRoutePolicy.CONSISTENT);
        materials.clear();
        assertThrows(UnsupportedOperationException.class, () -> declaration.materials().clear());
        var manager = new RecyclingManager();
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(declaration), false).publish();
        assertEquals(List.of(new MaterialStack(Materials.Iron, 5_000_000_000L), new MaterialStack(Materials.Gold, 7)),
                manager.getRecyclingData(key(Items.DIAMOND)).getMaterials());
    }

    @Test
    void sameNamedMaterialsFromDifferentNamespacesRemainDistinct() {
        Material first = new Material(new ResourceLocation("route_first", "metal")) {};
        Material second = new Material(new ResourceLocation("route_second", "metal")) {};
        assertEquals(first.getName(), second.getName());
        var manager = new RecyclingManager();
        var declaration = contribution("namespaces", 1, Map.of(), Map.of(first, 10L, second, 10L),
                RecyclingRoutePolicy.CONSISTENT);
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(declaration), false).publish();
        assertEquals(List.of(new MaterialStack(first, 10), new MaterialStack(second, 10)),
                manager.getRecyclingData(key(Items.DIAMOND)).getMaterials());
    }

    @Test
    void fractionalItemAndMaterialAmountsRoundAfterSummingTheSameMaterial() {
        var manager = new RecyclingManager();
        manager.registerRecyclingData(key(Items.APPLE), data(Materials.Iron, 100));
        var declaration = contribution("fractional", 3, Map.of(key(Items.APPLE), 1L), Map.of(Materials.Iron, 2L),
                RecyclingRoutePolicy.CONSISTENT);
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(declaration), false).publish();
        assertEquals(data(Materials.Iron, 34), manager.getRecyclingData(key(Items.DIAMOND)));
    }

    @Test
    void anUnknownMinimumRouteIsRetainedAndResolvesWhenUpstreamArrives() {
        var manager = new RecyclingManager();
        var downstream = new RecyclingOwner();
        var upstream = new RecyclingOwner();
        var unknown = contribution("unknown", 2, Map.of(key(Items.APPLE), 1L), Map.of(),
                RecyclingRoutePolicy.MINIMUM_INPUTS);
        manager.ownership().prepare(downstream, "routes", List.of(unknown), false).publish();
        assertTrue(manager.getRecyclingData(key(Items.DIAMOND)).getMaterials().isEmpty());
        var source = new RecyclingContribution(origin("source"), key(Items.APPLE), 1,
                Map.of(), Map.of(Materials.Iron, 100L), RecyclingRoutePolicy.REPLACE);
        manager.ownership().prepare(upstream, "routes", List.of(source), false).publish();
        assertEquals(data(Materials.Iron, 50), manager.getRecyclingData(key(Items.DIAMOND)));
        manager.ownership().prepare(upstream, "routes", List.of(), true).publish();
        assertTrue(manager.getRecyclingData(key(Items.DIAMOND)).getMaterials().isEmpty());
    }

    @Test
    void staticallyCompiledGroovyConstructsTheSameNativePolicyDescriptor() {
        var descriptor = (RecyclingContribution) new GroovyShell(getClass().getClassLoader()).evaluate(
                """
                        import groovy.transform.CompileStatic
                        import gregtech.api.recipes.*
                        import gregtech.api.definition.SourceOrigin
                        import gregtech.api.unification.stack.ItemAndMetadata
                        import gregtech.api.unification.material.Materials
                        import net.minecraft.init.Items
                        @CompileStatic
                        RecyclingContribution declaration() {
                            return new RecyclingContribution(new SourceOrigin('groovy', 'routes.groovy', 1, 1, 'material', 'recipes'),
                                new ItemAndMetadata(Items.DIAMOND, 0), 1, Collections.emptyMap(),
                                Collections.singletonMap(Materials.Iron, 5000000000L), RecyclingRoutePolicy.REPLACE)
                        }
                        declaration()
                        """);
        var manager = new RecyclingManager();
        manager.ownership().prepare(new RecyclingOwner(), "routes", List.of(descriptor), false).publish();
        assertEquals(data(Materials.Iron, 5_000_000_000L), manager.getRecyclingData(key(Items.DIAMOND)));
    }

    private static RecyclingContribution contribution(String name, int outputCount, Map<ItemAndMetadata, Long> inputs,
                                                      Map<Material, Long> materials, RecyclingRoutePolicy policy) {
        return new RecyclingContribution(origin(name), key(Items.DIAMOND), outputCount, inputs, materials, policy);
    }

    private static SourceOrigin origin(String name) {
        return new SourceOrigin("java", "RecyclingRoutes.java", 10, 1, name, "recipes");
    }

    private static ItemAndMetadata key(Item item) {
        return new ItemAndMetadata(item, 0);
    }

    private static RecyclingData data(Material material, long amount) {
        return new RecyclingData(new MaterialStack(material, amount));
    }
}

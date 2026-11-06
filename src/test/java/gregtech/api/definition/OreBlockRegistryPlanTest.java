package gregtech.api.definition;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.util.ResourceLocation;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OreBlockRegistryPlanTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("java", "OreContent.java", 9, 1, "zircon", "materials");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void conflictingOwnersRejectTheWholePlanWithBothMaterialIdentities() {
        var result = OreBlockRegistryPlan.prepareDefinitions(List.of(
                identity("gregtech:zircon", "gregtech:ore_zircon"),
                identity("susy:zircon", "gregtech:ore_zircon"),
                identity("pack:other", "pack:ore_other")));
        assertTrue(result.value().isEmpty());
        var diagnostic = result.diagnostics().getFirst();
        assertEquals("ore.registry_collision", diagnostic.code());
        assertEquals(ORIGIN, diagnostic.origin());
        assertTrue(diagnostic.message().contains("gregtech:zircon"));
        assertTrue(diagnostic.message().contains("susy:zircon"));
        assertTrue(diagnostic.format().contains("OreContent.java:9"));
    }

    @Test
    void explicitIdentityRetainsHistoricalPackNamesAndBothOreVariants() {
        var plan = OreBlockRegistryPlan.prepareDefinitions(List.of(
                identity("gregtech:zircon", "gregtech:ore_gregtech_zircon"),
                identity("susy:zircon", "gregtech:ore_zircon"))).value().orElseThrow();
        for (int group : new int[] { 0, 1, 2, 17 }) {
            assertEquals(new ResourceLocation("gregtech", "ore_zircon_" + group),
                    plan.blockName(new ResourceLocation("susy", "zircon"), group));
            assertEquals(new ResourceLocation("gregtech", "ore_gregtech_zircon_" + group),
                    plan.blockName(new ResourceLocation("gregtech", "zircon"), group));
        }
    }

    @Test
    void repeatedMaterialIsRejectedEvenWithDifferentBlockNames() {
        var result = OreBlockRegistryPlan.prepareDefinitions(List.of(
                identity("pack:one", "pack:first"), identity("pack:one", "pack:second")));
        assertTrue(result.value().isEmpty());
        assertEquals("ore.duplicate_material", result.diagnostics().getFirst().code());
    }

    @Test
    void preparedNamesDoNotShareTheCallerList() {
        var declarations = new ArrayList<>(List.of(identity("pack:one", "pack:ore_one")));
        var plan = OreBlockRegistryPlan.prepareDefinitions(declarations).value().orElseThrow();
        declarations.clear();
        assertEquals(new ResourceLocation("pack", "ore_one_0"),
                plan.blockName(new ResourceLocation("pack", "one"), 0));
    }

    @Test
    void unknownMaterialsAndNegativeStoneGroupsAreRejected() {
        var plan = OreBlockRegistryPlan.prepareDefinitions(List.of(identity("pack:one", "pack:ore_one")))
                .value().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> plan.blockName(new ResourceLocation("pack", "missing"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> plan.blockName(new ResourceLocation("pack", "one"), -1));
    }

    @Test
    void nativeMaterialPreparationPreservesDefaultNamesAndExcludesNonOres() {
        var plan = OreBlockRegistryPlan.prepare(List.of(Materials.Zircon, Materials.Water)).value().orElseThrow();
        assertEquals(new ResourceLocation("gregtech", "ore_zircon_0"),
                plan.blockName(Materials.Zircon.getResourceLocation(), 0));
        assertThrows(IllegalArgumentException.class, () -> plan.blockName(Materials.Water.getResourceLocation(), 0));
    }

    @Test
    void propertyIdentityMustBelongToItsMaterialAndPreparationSnapshotsIt() {
        var property = Materials.Zircon.getProperty(PropertyKey.ORE);
        var previous = property.getBlockIdentity();
        try {
            property.setBlockIdentity(identity("pack:other", "pack:ore_other"));
            var failed = OreBlockRegistryPlan.prepare(List.of(Materials.Zircon));
            assertTrue(failed.value().isEmpty());
            assertEquals("ore.material", failed.diagnostics().getFirst().code());
            property.setBlockIdentity(identity("gregtech:zircon", "pack:ore_native_zircon"));
            var plan = OreBlockRegistryPlan.prepare(List.of(Materials.Zircon)).value().orElseThrow();
            property.setBlockIdentity(null);
            assertEquals(new ResourceLocation("pack", "ore_native_zircon_1"),
                    plan.blockName(Materials.Zircon.getResourceLocation(), 1));
        } finally {
            property.setBlockIdentity(previous);
        }
    }

    @Test
    void numericRegistryEntriesRemainVisibleWhenAnAddonUsesTheDefaultRegistryFallback() throws Exception {
        var previousManager = GregTechAPI.materialManager;
        var constructor = MaterialRegistryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        GregTechAPI.materialManager = manager;
        try {
            manager.unfreezeRegistries();
            var nativeOre = Material.builder(270, new ResourceLocation("gregtech:zircon")).gem().ore().build();
            var packOre = Material.builder(7047, new ResourceLocation("susy:zircon")).gem().ore().build();
            // The retained pack has no distinct susy material registry: its name alias replaces the native alias,
            // while both numeric material identities remain in the default registry used by block creation.
            assertSame(manager.getRegistry("gregtech"), manager.getRegistry("susy"));
            nativeOre.getProperty(PropertyKey.ORE).setBlockIdentity(
                    identity("gregtech:zircon", "gregtech:ore_gregtech_zircon"));
            manager.closeRegistries();
            assertTrue(manager.getRegisteredMaterials().contains(nativeOre));
            assertTrue(manager.getRegisteredMaterials().contains(packOre));
            var plan = OreBlockRegistryPlan.prepareRegistries(manager.getRegistries()).value().orElseThrow();
            assertEquals(new ResourceLocation("gregtech:ore_gregtech_zircon_0"),
                    plan.blockName(nativeOre.getResourceLocation(), 0));
            assertEquals(new ResourceLocation("gregtech:ore_zircon_0"),
                    plan.blockName(packOre.getResourceLocation(), 0));
            assertSame(nativeOre, manager.getRegistry("gregtech").getObjectById(270));
            assertSame(packOre, manager.getRegistry("gregtech").getObjectById(7047));
        } finally {
            GregTechAPI.materialManager = previousManager;
        }
    }

    @Test
    void staticallyCompiledGroovyUsesTheSameImmutableIdentity() {
        var binding = new Binding();
        binding.setVariable("origin", ORIGIN);
        var result = new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.definition.*
                import net.minecraft.util.ResourceLocation
                @CompileStatic
                OreBlockIdentity declare(SourceOrigin source) {
                    new OreBlockIdentity(source, new ResourceLocation('pack:one'), new ResourceLocation('pack:ore_one'))
                }
                declare(origin)
                """);
        assertEquals(identity("pack:one", "pack:ore_one"), result);
    }

    private static OreBlockIdentity identity(String material, String base) {
        return new OreBlockIdentity(ORIGIN, new ResourceLocation(material), new ResourceLocation(base));
    }
}

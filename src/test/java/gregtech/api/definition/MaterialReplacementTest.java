package gregtech.api.definition;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.util.ResourceLocation;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialReplacementTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("groovy", "materials.groovy", 27, 4,
            "replace_alloy", "MaterialEvent");
    private IMaterialRegistryManager previous;
    private MaterialRegistryManager manager;

    @BeforeEach
    void bootstrap() throws Exception {
        Bootstrap.perform();
        previous = GregTechAPI.materialManager;
        var constructor = MaterialRegistryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
        GregTechAPI.materialManager = manager;
        manager.unfreezeRegistries();
    }

    @AfterEach
    void restore() {
        GregTechAPI.materialManager = previous;
    }

    @Test
    void explicitReplacementPreservesPhysicalEntriesAndExistingReferences() {
        var original = builder(61, "susy:shared_material").color(0x112233).build();
        var dependent = builder(63, "susy:dependent_material").components(original, 2).build();
        var updated = builder(62, "susy:shared_material").color(0x445566)
                .buildReplacing(new MaterialReplacement(ORIGIN, original));
        assertSame(updated, manager.getMaterial("susy:shared_material"));
        assertSame(updated, manager.getMaterial("shared_material"));
        assertSame(original, original.getRegistry().getObjectById(61));
        assertSame(updated, original.getRegistry().getObjectById(62));
        assertEquals(61, original.getRegistry().getIDForObject(original));
        assertEquals(62, original.getRegistry().getIDForObject(updated));
        assertEquals(0x112233, original.getMaterialRGB());
        assertSame(original, dependent.getMaterialComponents().getFirst().material);
        var physical = new ArrayList<Material>();
        original.getRegistry().forEach(physical::add);
        assertTrue(physical.containsAll(List.of(original, updated, dependent)));
        manager.closeRegistries();
        manager.freezeRegistries();
        assertSame(updated, manager.getMaterial("susy:shared_material"));
        assertSame(original, original.getRegistry().getObjectById(61));
    }

    @Test
    void staleExpectedDeclarationRejectsBeforePublishingAnotherSlot() {
        var original = builder(71, "susy:stale_material").build();
        var declaration = new MaterialReplacement(ORIGIN, original);
        var updated = builder(72, "susy:stale_material").buildReplacing(declaration);
        var error = assertThrows(MaterialReplacementException.class,
                () -> builder(73, "susy:stale_material").buildReplacing(declaration));
        assertEquals("material.stale", error.diagnostic().code());
        assertEquals(ORIGIN, error.diagnostic().origin());
        assertTrue(error.getMessage().contains("materials.groovy:27:4"));
        assertSame(updated, manager.getMaterial("susy:stale_material"));
        assertNull(original.getRegistry().getObjectById(73));
    }

    @Test
    void occupiedReplacementIdLeavesBothNamesAndIdsUnchanged() {
        var original = builder(81, "susy:original_material").build();
        var occupied = builder(82, "susy:occupied_material").build();
        var error = assertThrows(MaterialReplacementException.class,
                () -> builder(82, "susy:original_material").buildReplacing(new MaterialReplacement(ORIGIN, original)));
        assertEquals("material.numeric_id", error.diagnostic().code());
        assertSame(original, manager.getMaterial("susy:original_material"));
        assertSame(occupied, manager.getMaterial("susy:occupied_material"));
        assertSame(occupied, original.getRegistry().getObjectById(82));
    }

    @Test
    void replacementCannotReuseThePriorPhysicalId() {
        var original = builder(91, "susy:fixed_material").build();
        assertThrows(MaterialReplacementException.class,
                () -> builder(91, "susy:fixed_material").buildReplacing(new MaterialReplacement(ORIGIN, original)));
        assertSame(original, manager.getMaterial("susy:fixed_material"));
    }

    @Test
    void replacementCannotChangeTheQualifiedIdentity() {
        var original = builder(101, "gregtech:shared_material").build();
        var error = assertThrows(MaterialReplacementException.class,
                () -> builder(102, "susy:shared_material").buildReplacing(new MaterialReplacement(ORIGIN, original)));
        assertEquals("material.identity", error.diagnostic().code());
        assertSame(original, manager.getMaterial("gregtech:shared_material"));
        assertNull(manager.getMaterial("susy:shared_material"));
        assertNull(original.getRegistry().getObjectById(102));
    }

    @Test
    void replacementAfterMaterialEventRejectsWithoutPublishing() {
        var original = builder(111, "susy:closed_material").build();
        manager.closeRegistries();
        var error = assertThrows(MaterialReplacementException.class,
                () -> builder(112, "susy:closed_material").buildReplacing(new MaterialReplacement(ORIGIN, original)));
        assertEquals("material.phase", error.diagnostic().code());
        assertSame(original, manager.getMaterial("susy:closed_material"));
        assertNull(original.getRegistry().getObjectById(112));
    }

    @Test
    void explicitReplacementDoesNotPermitAnOrdinaryDuplicate() {
        var original = builder(121, "susy:checked_material").build();
        var updated = builder(122, "susy:checked_material").buildReplacing(new MaterialReplacement(ORIGIN, original));
        assertThrows(IllegalArgumentException.class, () -> builder(123, "susy:checked_material").build());
        assertSame(updated, manager.getMaterial("susy:checked_material"));
        assertNull(original.getRegistry().getObjectById(123));
    }

    @Test
    void dynamicAndStaticallyCheckedGroovyUseTheSameReplacementContract() {
        for (String annotation : List.of("", "@groovy.transform.CompileStatic")) {
            int id = annotation.isEmpty() ? 131 : 141;
            String name = annotation.isEmpty() ? "susy:dynamic_material" : "susy:static_material";
            var original = builder(id, name).build();
            var binding = new Binding();
            binding.setVariable("prior", original);
            binding.setVariable("origin", ORIGIN);
            var result = new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                    import gregtech.api.definition.MaterialReplacement
                    import gregtech.api.definition.SourceOrigin
                    import gregtech.api.unification.material.Material
                    %s
                    Material replace(Material previous, SourceOrigin source) {
                        Material.builder(previous.getId() + 1, previous.getResourceLocation()).dust()
                            .buildReplacing(new MaterialReplacement(source, previous))
                    }
                    replace(prior, origin)
                    """.formatted(annotation));
            assertSame(result, manager.getMaterial(name));
            assertSame(original, original.getRegistry().getObjectById(id));
        }
    }

    private static Material.Builder builder(int id, String name) {
        return Material.builder(id, new ResourceLocation(name)).dust();
    }
}

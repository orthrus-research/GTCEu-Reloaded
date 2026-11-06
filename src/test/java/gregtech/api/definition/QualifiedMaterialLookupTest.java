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

import static org.junit.jupiter.api.Assertions.*;

class QualifiedMaterialLookupTest {

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
        manager.createRegistry("addon");
        manager.unfreezeRegistries();
    }

    @AfterEach
    void restore() {
        GregTechAPI.materialManager = previous;
    }

    @Test
    void qualifiedNamesSurviveSharedPhysicalRegistryAliases() {
        var core = material(391, "gregtech:shared_alloy");
        var pack = material(8710, "susy:shared_alloy");
        assertSame(core.getRegistry(), pack.getRegistry());
        assertSame(core, manager.getMaterial("gregtech:shared_alloy"));
        assertSame(pack, manager.getMaterial("susy:shared_alloy"));
        assertSame(core, core.getRegistry().getObjectById(391));
        assertSame(pack, pack.getRegistry().getObjectById(8710));
        manager.closeRegistries();
        manager.freezeRegistries();
        assertSame(core, manager.getMaterial("gregtech:shared_alloy"));
        assertSame(pack, manager.getMaterial("susy:shared_alloy"));
    }

    @Test
    void unknownNamespaceCannotResolveADefaultRegistryMaterial() {
        var core = material(42, "gregtech:only_core");
        assertSame(core, manager.getMaterial("only_core"));
        assertNull(manager.getMaterial("missing:only_core"));
        assertNull(manager.getMaterial("missing:missing"));
    }

    @Test
    void separateRegistryAndDefaultStorageRemainDistinct() {
        var core = material(51, "gregtech:separate_alloy");
        var addon = material(51, "addon:separate_alloy");
        assertSame(core, manager.getMaterial("gregtech:separate_alloy"));
        assertSame(addon, manager.getMaterial("addon:separate_alloy"));
        assertNotSame(core.getRegistry(), addon.getRegistry());
        assertNull(manager.getMaterial("susy:separate_alloy"));
    }

    @Test
    void duplicateQualifiedIdentityCannotOrphanItsEarlierNumericEntry() {
        var original = material(61, "gregtech:duplicate_name");
        assertThrows(IllegalArgumentException.class, () -> material(62, "gregtech:duplicate_name"));
        assertSame(original, manager.getMaterial("gregtech:duplicate_name"));
        assertSame(original, original.getRegistry().getObjectById(61));
        assertNull(original.getRegistry().getObjectById(62));
    }

    @Test
    void rejectedNumericIdDoesNotPublishAQualifiedOrShortName() {
        var original = material(71, "gregtech:occupied_id");
        assertThrows(IllegalArgumentException.class, () -> material(71, "susy:rejected_id"));
        assertSame(original, manager.getMaterial("gregtech:occupied_id"));
        assertNull(manager.getMaterial("susy:rejected_id"));
        assertNull(manager.getMaterial("rejected_id"));
    }

    @Test
    void staticallyCheckedGroovyUsesTheExactNativeIdentity() {
        var core = material(81, "gregtech:groovy_alloy");
        var pack = material(82, "susy:groovy_alloy");
        var binding = new Binding();
        binding.setVariable("manager", manager);
        var result = new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                import gregtech.api.unification.material.registry.IMaterialRegistryManager
                import gregtech.api.unification.material.Material
                import groovy.transform.CompileStatic
                @CompileStatic
                List<Material> find(IMaterialRegistryManager registry) {
                    [registry.getMaterial('gregtech:groovy_alloy'), registry.getMaterial('susy:groovy_alloy')]
                }
                find(manager)
                """);
        assertEquals(java.util.List.of(core, pack), result);
    }

    private static Material material(int id, String name) {
        return Material.builder(id, new ResourceLocation(name)).dust().build();
    }
}

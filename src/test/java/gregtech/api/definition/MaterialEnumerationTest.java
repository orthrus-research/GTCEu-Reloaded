package gregtech.api.definition;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.fluids.FluidBuilder;
import gregtech.api.fluids.GTFluidRegistration;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialEnumerationTest {

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
    void everyNamespaceIsEnumeratedWhenNamesSharePhysicalStorage() {
        var core = material(41, "gregtech:shared_material");
        var pack = material(42, "susy:shared_material");
        var addon = material(41, "addon:shared_material");
        assertEquals(List.of(core, pack), new ArrayList<>(core.getRegistry().getAllMaterials()));
        manager.closeRegistries();
        assertEquals(3, manager.getRegisteredMaterials().size());
        assertTrue(manager.getRegisteredMaterials().containsAll(List.of(core, pack, addon)));
        assertThrows(UnsupportedOperationException.class, () -> core.getRegistry().getAllMaterials().clear());
        manager.freezeRegistries();
        assertEquals(3, manager.getRegisteredMaterials().size());
    }

    @Test
    void logicalEnumerationSelectsOnlyTheExplicitReplacementButNumericStorageRetainsBoth() {
        var core = material(51, "gregtech:replaced_material");
        var original = material(52, "susy:replaced_material");
        var replacement = Material.builder(53, new ResourceLocation("susy:replaced_material")).dust()
                .buildReplacing(new MaterialReplacement(new SourceOrigin("java", "MaterialEnumerationTest", 0, 0,
                        "replacement", "MaterialEvent"), original));
        manager.closeRegistries();
        assertEquals(List.of(core, replacement), new ArrayList<>(core.getRegistry().getAllMaterials()));
        assertTrue(manager.getRegisteredMaterials().contains(core));
        assertTrue(manager.getRegisteredMaterials().contains(replacement));
        assertFalse(manager.getRegisteredMaterials().contains(original));
        assertSame(original, core.getRegistry().getObjectById(52));
        var physical = new ArrayList<Material>();
        core.getRegistry().forEach(physical::add);
        assertEquals(List.of(core, original, replacement), physical);
    }

    @Test
    void fluidRegistrationVisitsBothOwnersAndHonorsTheirExplicitFluidNames() {
        var core = Material.builder(61, new ResourceLocation("gregtech:enumeration_fluid"))
                .liquid(new FluidBuilder().name("core_enumeration_fixture").temperature(420)).build();
        var pack = Material.builder(62, new ResourceLocation("susy:enumeration_fluid"))
                .liquid(new FluidBuilder().name("pack_enumeration_fixture").temperature(680)).build();
        manager.closeRegistries();
        manager.freezeRegistries();
        GTFluidRegistration.INSTANCE.register();
        assertNotNull(core.getFluid());
        assertNotNull(pack.getFluid());
        assertNotSame(core.getFluid(), pack.getFluid());
        assertEquals("core_enumeration_fixture", core.getFluid().getName());
        assertEquals("pack_enumeration_fixture", pack.getFluid().getName());
        assertEquals(420, core.getFluid().getTemperature());
        assertEquals(680, pack.getFluid().getTemperature());
    }

    private static Material material(int id, String name) {
        return Material.builder(id, new ResourceLocation(name)).dust().build();
    }
}

package gregtech.api.definition;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentDefinitionsTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("java", "PackContent.java", 3, 1, "testium", "preInit");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void rejectsAnEntireInvalidBatchBeforeAnyContentIsQueued() {
        OreItemRegistry registry = new OreItemRegistry(new ResourceLocation("test", "invalid_batch"));
        var valid = item(23, "testium");
        var invalid = new OreItemDefinition(ORIGIN, 23, "Bad Name", -1, "missing_set", "missing_prefix", "");
        var result = registry.register(List.of(valid, invalid));
        assertTrue(result.value().isEmpty());
        assertEquals(3, result.diagnostics().size());
        assertTrue(registry.definitions().isEmpty());
        assertTrue(result.diagnostics().stream().allMatch(d -> d.origin().equals(ORIGIN)));
        assertThrows(IllegalStateException.class, () -> registry.stack(23, 1));
    }

    @Test
    void javaAndGroovyContentPreserveSavedCarrierMetadataOreNameAndFormula() {
        var definition = item(23, "pack_testium");
        Binding binding = new Binding();
        binding.setVariable("origin", ORIGIN);
        var groovy = new GroovyShell(getClass().getClassLoader(), binding).evaluate(
                """
                        new gregtech.api.definition.OreItemDefinition(origin, 23, 'pack_testium', 0x123456, 'metallic', 'dust', 'Fe2O3')
                        """);
        assertEquals(definition, groovy);
        OreItemRegistry registry = new OreItemRegistry(new ResourceLocation("gregtech", "meta_oredict_item_ct"));
        assertTrue(registry.register(List.of(definition)).value().isPresent());
        var carrier = registry.freeze();
        assertSame(carrier, registry.freeze());
        ForgeRegistries.ITEMS.register(carrier);
        carrier.registerSubItems();
        assertEquals("gregtech:meta_oredict_item_ct", carrier.getRegistryName().toString());
        assertEquals("Fe₂O₃", carrier.OREDICT_TO_FORMULA.get("dustPackTestium"));
        assertTrue(OreDictionary.getOres("dustPackTestium").stream()
                .anyMatch(s -> s.getItem() == carrier && s.getMetadata() == 23));
        assertEquals("pack_testium_dust", carrier.getAllItems().iterator().next().unlocalizedName);

        // Persisted-format fixture, not a claim of a native copied-world migration run.
        NBTTagCompound saved = new NBTTagCompound();
        saved.setString("id", "gregtech:meta_oredict_item_ct");
        saved.setShort("Damage", (short) 23);
        saved.setByte("Count", (byte) 7);
        ItemStack restored = new ItemStack(saved);
        assertSame(carrier, restored.getItem());
        assertEquals(23, restored.getMetadata());
        assertEquals(7, restored.getCount());
        assertEquals(saved, restored.serializeNBT());
        assertTrue(ItemStack.areItemStacksEqual(restored, registry.stack(23, 7)));
        assertEquals("content.frozen", registry.register(List.of(item(24, "other"))).diagnostics().getFirst().code());
        assertEquals(1, registry.definitions().size());
    }

    @Test
    void coilStatisticsAreNativeAndInvalidReplacementLeavesTheExistingEntry() {
        var state = Blocks.STONE.getDefaultState();
        IHeatingCoilBlockStats previous = GregTechAPI.HEATING_COILS.get(state);
        try {
            var coil = new HeatingCoilDefinition(ORIGIN, "test_coil", 1800, 2, 3, 1, null);
            assertTrue(ContentDefinitions.coil(state, coil).value().isPresent());
            assertSame(coil, GregTechAPI.HEATING_COILS.get(state));
            assertEquals(1800, coil.getCoilTemperature());
            assertEquals(2, coil.getLevel());
            assertEquals(3, coil.getEnergyDiscount());
            assertEquals(1, coil.getTier());
            assertEquals(0xFFFFFFFF, coil.getColor());
            assertTrue(ContentDefinitions.coil(state, new HeatingCoilDefinition(ORIGIN, "invalid", 0, 1, 1, 0, null))
                    .value().isEmpty());
            assertSame(coil, GregTechAPI.HEATING_COILS.get(state));
            assertSame(coil, ContentDefinitions.removeCoil(state));
        } finally {
            if (previous == null) GregTechAPI.HEATING_COILS.remove(state);
            else GregTechAPI.HEATING_COILS.put(state, previous);
        }
    }

    private static OreItemDefinition item(int id, String name) {
        return new OreItemDefinition(ORIGIN, id, name, 0x123456, "metallic", "dust", "Fe2O3");
    }

    @Test
    void earlyDeclarationsDeferRegistryLookupAndUnknownReferencesPreventFreezing() {
        var registry = new OreItemRegistry(new ResourceLocation("test", "early_content"));
        var markerRegistry = GregTechAPI.markerMaterialRegistry;
        try {
            GregTechAPI.markerMaterialRegistry = null;
            assertTrue(registry.register(List.of(item(23, "early_item"))).value().isPresent());
            assertEquals("content.registry.not_ready", registry.resolveReferences().diagnostics().getFirst().code());
            assertThrows(IllegalStateException.class, registry::freeze);
            assertThrows(IllegalStateException.class, () -> registry.stack(23, 1));
        } finally {
            GregTechAPI.markerMaterialRegistry = markerRegistry;
        }
        assertTrue(registry.resolveReferences().value().isPresent());
        var invalid = new OreItemRegistry(new ResourceLocation("test", "unknown_content"));
        assertTrue(invalid.register(List.of(new OreItemDefinition(ORIGIN, 24, "unknown_item", 0,
                "missing_set", "missing_prefix", ""))).value().isPresent());
        assertEquals(List.of("content.icon.unknown", "content.prefix.unknown"),
                invalid.resolveReferences().diagnostics().stream().map(DefinitionDiagnostic::code).toList());
        assertThrows(IllegalStateException.class, invalid::freeze);
        assertThrows(IllegalStateException.class, () -> invalid.stack(24, 1));
    }
}

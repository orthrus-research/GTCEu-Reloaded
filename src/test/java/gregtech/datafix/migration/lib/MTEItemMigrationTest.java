package gregtech.datafix.migration.lib;

import gregtech.Bootstrap;
import gregtech.datafix.migration.impl.MigrateMTEItems;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraftforge.common.util.CompoundDataFixer;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MTEItemMigrationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void registryRemappingRequiresTheOldItemAndADeclaredOwner() {
        var registries = new MTERegistriesMigrator();
        registries.migrate("future_addon", (short) 7);
        registries.migrate(7, 8);
        registries.migrate(9, 10);
        var fix = new MigrateMTEItems(registries);
        for (String id : List.of("minecraft:diamond_sword", "gregtech:meta_item_1", "future_addon:mte",
                "other_addon:mte")) {
            var saved = item(id, 7);
            assertEquals(saved.copy(), fix.fixTagCompound(saved));
        }
        var undeclared = item("gregtech:machine", 9);
        assertEquals(undeclared.copy(), fix.fixTagCompound(undeclared));
        var saved = item("gregtech:machine", 7);
        var expected = item("future_addon:mte", 8);
        assertEquals(expected, fix.fixTagCompound(saved));
        assertEquals(expected, fix.fixTagCompound(saved));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void addonMetadataUsesTheDestinationOwnerInEitherRegistryFixOrder(boolean registryFirst) {
        var registries = new MTERegistriesMigrator();
        registries.migrate("future_addon", (short) 7);
        registries.migrate(7, 8);
        // A different old machine owns 8. Its remap must not touch the already converted item.
        registries.migrate("other_addon", (short) 8);
        registries.migrate(8, 9);
        var data = dataMigrator(registries);
        data.migrateMTEMeta("future_addon", 8, 0);
        data.migrateMTEMeta("gregtech", 8, 100);
        var registryFix = new MigrateMTEItems(registries);
        var dataFix = new MigrateMTEItems(data);
        var saved = item("gregtech:machine", 7);
        if (registryFirst) {
            registryFix.fixTagCompound(saved);
            dataFix.fixTagCompound(saved);
        } else {
            dataFix.fixTagCompound(saved);
            registryFix.fixTagCompound(saved);
        }
        assertEquals(item("future_addon:mte", 0), saved);
    }

    @Test
    void metadataRulesDoNotChangeOtherItemsInTheSameNamespaceOrOtherOwners() {
        var registries = new MTERegistriesMigrator();
        var data = dataMigrator(registries);
        data.migrateMTEMeta("future_addon", 8, 0);
        var fix = new MigrateMTEItems(data);
        for (String id : List.of("future_addon:tool", "future_addon:meta_item", "other_addon:mte",
                "gregtech:machine")) {
            var saved = item(id, 8);
            assertEquals(saved.copy(), fix.fixTagCompound(saved));
        }
        assertEquals(item("future_addon:mte", 0), fix.fixTagCompound(item("future_addon:mte", 8)));
        var unmapped = item("future_addon:mte", 19);
        assertEquals(unmapped.copy(), fix.fixTagCompound(unmapped));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void dynamicAndTypedGroovyDeclareTheSameOwnerMappings(boolean checked) {
        var registries = new MTERegistriesMigrator();
        var data = dataMigrator(registries);
        var bindings = new Binding();
        bindings.setVariable("registries", registries);
        bindings.setVariable("data", data);
        new GroovyShell(getClass().getClassLoader(), bindings).evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.datafix.migration.lib.MTERegistriesMigrator
                import gregtech.datafix.migration.lib.MTEDataMigrator
                %s
                class ContentOwner {
                    static void declare(MTERegistriesMigrator registries, MTEDataMigrator data) {
                        registries.migrate('script_owner', (short) 27001)
                        registries.migrate(27001, 41)
                        data.migrateMTEMeta('script_owner', 41, 0)
                    }
                }
                ContentOwner.declare(registries, data)
                """.formatted(checked ? "@CompileStatic" : ""));
        assertEquals(item("script_owner:mte", 0),
                new MigrateMTEItems(data).fixTagCompound(item("gregtech:machine", 27001)));
    }

    private static MTEDataMigrator dataMigrator(MTERegistriesMigrator registries) {
        var fixer = new CompoundDataFixer(new DataFixer(1343));
        return new MTEDataMigrator(fixer.init("future_addon", 2), 2, registries);
    }

    private static NBTTagCompound item(String id, int metadata) {
        var item = new NBTTagCompound();
        item.setString("id", id);
        item.setShort("Damage", (short) metadata);
        item.setInteger("Count", 64);
        var payload = new NBTTagCompound();
        payload.setLong("storedEnergy", 123456);
        item.setTag("tag", payload);
        return item;
    }
}

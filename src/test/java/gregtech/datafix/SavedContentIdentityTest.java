package gregtech.datafix;

import gregtech.BootstrapExtension;
import gregtech.api.GregTechAPI;
import gregtech.api.items.materialitem.MetaPrefixItem;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.MaterialRegistry;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the numeric identities saved worlds store: material ids, meta item damage values and machine ids, each
 * against a checked-in listing under {@code src/test/resources/gregtech/golden}.
 * <p>
 * The listings hold what the bootstrap registers: GregTech's own content with the default configuration and no
 * optional mods, so high-tier content and the Applied Energistics 2 parts are not in them.
 */
@ExtendWith(BootstrapExtension.class)
class SavedContentIdentityTest {

    private static final String GOLDEN = "/gregtech/golden/";
    private static final Path ACTUAL = Path.of("build", "golden");

    @Test
    void materialIdsAreUnchanged() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        for (MaterialRegistry registry : sorted(GregTechAPI.materialManager.getRegistries(),
                Comparator.comparing(MaterialRegistry::getModid))) {
            for (Material material : sorted(registry, Comparator.comparingInt(Material::getId))) {
                found.put(registry.getModid() + ' ' + material.getId(), material.getResourceLocation().toString());
            }
        }
        check("material_ids.list", "Material ids, as <registry> <id> <material>.",
                "a material's id is the metadata of its blocks, items and pipes", found);
    }

    @Test
    void metaItemDamageValuesAreUnchanged() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        List<MetaItem<?>> items = new ArrayList<>();
        for (MetaItem<?> item : MetaItem.getMetaItems()) {
            ResourceLocation name = item.getRegistryName();
            // prefix items take their damage values from material ids, which the material listing pins
            if (name != null && !(item instanceof MetaPrefixItem) &&
                    ForgeRegistries.ITEMS.getValue(name) == item) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(item -> item.getRegistryName().toString()));
        for (MetaItem<?> item : items) {
            for (MetaItem<?>.MetaValueItem value : sorted(item.getAllItems(),
                    Comparator.comparingInt(value -> value.getStackForm().getItemDamage()))) {
                found.put(item.getRegistryName().toString() + ' ' + value.getStackForm().getItemDamage(),
                        value.unlocalizedName);
            }
        }
        check("meta_item_ids.list", "Meta item damage values, as <item> <damage> <value name>.",
                "an item stack saves its damage value", found);
    }

    @Test
    void machineIdsAreUnchanged() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        for (MTERegistry registry : sorted(GregTechAPI.mteManager.getRegistries(),
                Comparator.comparing(MTERegistry::getModid))) {
            for (MetaTileEntity machine : sorted(registry, Comparator.comparingInt(registry::getIDForObject))) {
                found.put(registry.getModid() + ' ' + registry.getIDForObject(machine),
                        machine.metaTileEntityId.toString());
            }
        }
        check("machine_ids.list", "Machine ids, as <registry> <id> <machine>.",
                "a placed machine and its item save the machine's id", found);
    }

    private static <T> List<T> sorted(Iterable<T> values, Comparator<? super T> order) {
        List<T> list = new ArrayList<>();
        values.forEach(list::add);
        list.sort(order);
        return list;
    }

    /**
     * Compares the identities found with the listing exactly; on a difference, writes the found listing under
     * {@code build/golden} and fails naming every changed, missing and new identity.
     */
    private static void check(String file, String format, String why, Map<String, String> found)
                                                                                                 throws IOException {
        Map<String, String> expected = read(file);
        if (expected.equals(found)) return;

        List<String> changes = new ArrayList<>();
        expected.forEach((key, name) -> {
            String now = found.get(key);
            if (now == null) changes.add("removed " + key + ' ' + name);
            else if (!now.equals(name)) changes.add("changed " + key + ' ' + name + " -> " + now);
        });
        found.forEach((key, name) -> {
            if (!expected.containsKey(key)) changes.add("added " + key + ' ' + name);
        });
        if (changes.isEmpty()) changes.add("the same identities in another order");
        Path written = write(file, format, found);

        StringBuilder message = new StringBuilder()
                .append(file).append(" no longer matches the registered content (").append(why).append(").\n")
                .append("Changing or removing an id breaks every saved world that holds it: keep the id, or add a ")
                .append("versioned datafix (see gregtech.datafix) and then update the listing. A new id only ")
                .append("needs adding to the listing.\n")
                .append("The ids found now are in ").append(written.toAbsolutePath()).append('\n');
        changes.stream().limit(40).forEach(change -> message.append("  ").append(change).append('\n'));
        if (changes.size() > 40) message.append("  ... and ").append(changes.size() - 40).append(" more\n");
        fail(message.toString());
    }

    private static Map<String, String> read(String file) throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        InputStream stream = SavedContentIdentityTest.class.getResourceAsStream(GOLDEN + file);
        if (stream == null) return expected;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int name = line.lastIndexOf(' ');
                expected.put(line.substring(0, name), line.substring(name + 1));
            }
        }
        return expected;
    }

    private static Path write(String file, String format, Map<String, String> found) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# " + format);
        lines.add("# GregTech's content as the test bootstrap registers it: default configuration, no optional mods.");
        lines.add(
                "# Saved worlds store these ids. SavedContentIdentityTest compares this listing exactly: changing or");
        lines.add(
                "# removing an id needs a versioned datafix (see gregtech.datafix); a new id only needs adding here.");
        found.forEach((key, name) -> lines.add(key + ' ' + name));
        Files.createDirectories(ACTUAL);
        Path path = ACTUAL.resolve(file);
        Files.write(path, lines, StandardCharsets.UTF_8);
        return path;
    }
}

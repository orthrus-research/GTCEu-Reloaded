package gregtech.api.recipes.fixtures;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minimal native output forms, restored after tests; no global OreDictionary or item registry writes. */
public final class RecyclingUnificationFixture implements AutoCloseable {

    private final Map<Field, Map<Object, Object>> maps = new HashMap<>();
    private final Map<OrePrefix, Set<Object>> generated = new HashMap<>();
    private final Field generatedField;

    @SuppressWarnings("unchecked")
    public RecyclingUnificationFixture() throws ReflectiveOperationException {
        this(List.of(Materials.Iron, Materials.Copper, Materials.WroughtIron),
                List.of(OrePrefix.dust, OrePrefix.ingot));
    }

    @SuppressWarnings("unchecked")
    public RecyclingUnificationFixture(List<Material> materials, List<OrePrefix> prefixes)
                                                                                           throws ReflectiveOperationException {
        for (String name : List.of("stackUnificationInfo", "stackUnificationItems", "stackOreDictName",
                "oreDictNameStacks", "oreRegistrations")) {
            var field = OreDictUnifier.class.getDeclaredField(name);
            field.setAccessible(true);
            Map<Object, Object> values = (Map<Object, Object>) field.get(null);
            Map<Object, Object> copy = new HashMap<>();
            values.forEach((key, value) -> {
                if (name.equals("oreRegistrations")) {
                    var variants = new Int2ObjectOpenHashMap<Map<String, Object>>();
                    ((Map<Integer, Map<String, Object>>) value)
                            .forEach((meta, bindings) -> variants.put(meta.intValue(),
                                    new java.util.LinkedHashMap<>(bindings)));
                    copy.put(key, variants);
                } else copy.put(key, value instanceof List<?> list ? new ArrayList<>(list) : value);
            });
            maps.put(field, copy);
        }
        generatedField = OrePrefix.class.getDeclaredField("generatedMaterials");
        generatedField.setAccessible(true);
        for (var prefix : prefixes) {
            generated.put(prefix, new HashSet<>((Set<Object>) generatedField.get(prefix)));
        }
        for (var material : materials) {
            for (var prefix : prefixes) {
                var item = new Item().setRegistryName("recycling_fixture", prefix.name() + "_" + material.getName());
                OreDictUnifier.onItemRegistration(new OreDictionary.OreRegisterEvent(
                        prefix.name() + material.toCamelCaseString(), new ItemStack(item)));
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void close() throws IllegalAccessException {
        for (var entry : maps.entrySet()) {
            Map<Object, Object> values = (Map<Object, Object>) entry.getKey().get(null);
            values.clear();
            values.putAll(entry.getValue());
        }
        for (var entry : generated.entrySet()) {
            Set<Object> values = (Set<Object>) generatedField.get(entry.getKey());
            values.clear();
            values.addAll(entry.getValue());
        }
    }
}

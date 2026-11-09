package gregtech.api.recipes;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detached native tables. Registry objects and their live collection views survive publication.
 * <p>
 * {@link #apply} replaces whole tables, so a concurrent reader can observe an intermediate state. Publication is
 * therefore only supported while every reader is quiescent: registration runs on the owning thread during load,
 * and {@code /gs reload} runs on the server thread while the integrated client waits for the reload to finish and
 * HEI rebuilds its lookups afterwards. A per-name diff would not make concurrent reads safe either, because the
 * underlying Forge maps are not thread-safe. Callers that publish while another thread may read must synchronize
 * with that reader first.
 */
final class CraftingRegistryImage {

    private final Map<ResourceLocation, IRecipe> names;
    private final Map<Integer, IRecipe> ids;
    private final Map<Object, IRecipe> owners;
    private final BitSet availability;
    private final Set<ResourceLocation> dummies;
    private final Map<ResourceLocation, ResourceLocation> aliases;
    private final Map<ResourceLocation, List<IRecipe>> overrides = new LinkedHashMap<>();
    private final Set<Integer> blocked;

    CraftingRegistryImage(ForgeRegistry<IRecipe> registry) {
        names = snapshot(CraftingRegistryAccess.names(registry));
        ids = snapshot(CraftingRegistryAccess.ids(registry));
        owners = snapshot(CraftingRegistryAccess.owners(registry));
        availability = (BitSet) CraftingRegistryAccess.availability(registry).clone();
        dummies = Set.copyOf(CraftingRegistryAccess.dummies(registry));
        aliases = Map.copyOf(CraftingRegistryAccess.aliases(registry));
        CraftingRegistryAccess.overrides(registry).asMap()
                .forEach((key, values) -> overrides.put(key, List.copyOf(values)));
        blocked = Set.copyOf(CraftingRegistryAccess.blocked(registry));
    }

    boolean matches(ForgeRegistry<IRecipe> registry) {
        if (!same(names, CraftingRegistryAccess.names(registry)) || !same(ids, CraftingRegistryAccess.ids(registry)) ||
                !same(owners, CraftingRegistryAccess.owners(registry)) ||
                !availability.equals(CraftingRegistryAccess.availability(registry)) ||
                !dummies.equals(CraftingRegistryAccess.dummies(registry)) ||
                !aliases.equals(CraftingRegistryAccess.aliases(registry)) ||
                !blocked.equals(CraftingRegistryAccess.blocked(registry)))
            return false;
        var current = CraftingRegistryAccess.overrides(registry).asMap();
        if (!current.keySet().equals(overrides.keySet())) return false;
        for (var entry : overrides.entrySet()) {
            var actual = List.copyOf(current.get(entry.getKey()));
            if (actual.size() != entry.getValue().size()) return false;
            for (int i = 0; i < actual.size(); i++) if (actual.get(i) != entry.getValue().get(i)) return false;
        }
        return true;
    }

    void apply(ForgeRegistry<IRecipe> registry) {
        replace(CraftingRegistryAccess.names(registry), names);
        replace(CraftingRegistryAccess.ids(registry), ids);
        replace(CraftingRegistryAccess.owners(registry), owners);
        var bits = CraftingRegistryAccess.availability(registry);
        bits.clear();
        bits.or(availability);
        CraftingRegistryAccess.dummies(registry).clear();
        CraftingRegistryAccess.dummies(registry).addAll(dummies);
        replace(CraftingRegistryAccess.aliases(registry), aliases);
        CraftingRegistryAccess.overrides(registry).clear();
        overrides.forEach(CraftingRegistryAccess.overrides(registry)::putAll);
        CraftingRegistryAccess.blocked(registry).clear();
        CraftingRegistryAccess.blocked(registry).addAll(blocked);
    }

    void preserveExistingOrder(ForgeRegistry<IRecipe> registry) {
        preserveOrder(CraftingRegistryAccess.names(registry), names);
        preserveOrder(CraftingRegistryAccess.ids(registry), ids);
        preserveOrder(CraftingRegistryAccess.owners(registry), owners);
    }

    private static <K, V> void preserveOrder(Map<K, V> target, Map<K, V> previous) {
        Map<K, V> ordered = new LinkedHashMap<>();
        for (K key : previous.keySet()) if (target.containsKey(key)) ordered.put(key, target.get(key));
        target.forEach(ordered::putIfAbsent);
        replace(target, ordered);
    }

    private static <K> boolean same(Map<K, IRecipe> expected, Map<K, IRecipe> actual) {
        if (expected.size() != actual.size()) return false;
        var current = actual.entrySet().iterator();
        for (var entry : expected.entrySet()) {
            var other = current.next();
            if (!entry.getKey().equals(other.getKey()) || entry.getValue() != other.getValue()) return false;
        }
        return true;
    }

    private static <K, V> Map<K, V> snapshot(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <K, V> void replace(Map<K, V> target, Map<K, V> values) {
        target.clear();
        target.putAll(values);
    }
}

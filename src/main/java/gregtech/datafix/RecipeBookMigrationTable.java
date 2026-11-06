package gregtech.datafix;

import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Forward player-data mappings; registering a mapping does not register a crafting recipe. */
public final class RecipeBookMigrationTable {

    public static final RecipeBookMigrationTable GENERATED = new RecipeBookMigrationTable();

    private final Map<ResourceLocation, Set<ResourceLocation>> mappings = new TreeMap<>();

    public synchronized void register(ResourceLocation previous, ResourceLocation current) {
        Objects.requireNonNull(previous, "previous recipe name");
        Objects.requireNonNull(current, "current recipe name");
        mappings.computeIfAbsent(previous, ignored -> new TreeSet<>()).add(current);
    }

    public synchronized List<ResourceLocation> targets(ResourceLocation previous) {
        var targets = mappings.get(previous);
        return targets == null ? List.of() : List.copyOf(targets);
    }

    public synchronized Map<ResourceLocation, List<ResourceLocation>> snapshot() {
        var result = new TreeMap<ResourceLocation, List<ResourceLocation>>();
        mappings.forEach((previous, targets) -> result.put(previous, List.copyOf(targets)));
        return Collections.unmodifiableMap(result);
    }
}

package gregtech.api.recipes;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Native publication participant for already-compiled recipes; use only on a quiescent registration thread. */
@ApiStatus.Internal
public final class CraftingRegistryTransaction {

    private final Thread thread = Thread.currentThread();
    private final List<ForgeRegistry<IRecipe>> registries;
    private final List<CraftingRegistryImage> before;
    private final List<CraftingRegistryImage> after;
    private final List<Boolean> locks;
    private final CraftingRegistryRestoration restoration;
    private final boolean managedStages;
    private boolean published;
    private boolean consumed;

    /** Null values disable existing names; removing a name absent from ACTIVE is an intentional no-op. */
    public static CraftingRegistryTransaction prepare(Map<ResourceLocation, IRecipe> replacements) {
        return prepare(currentRegistries(), replacements);
    }

    static List<ForgeRegistry<IRecipe>> currentRegistries() {
        List<ForgeRegistry<IRecipe>> registries = new ArrayList<>();
        for (var manager : List.of(RegistryManager.ACTIVE, RegistryManager.FROZEN, RegistryManager.VANILLA)) {
            var registry = manager.getRegistry(IRecipe.class);
            if (registry != null && !registries.contains(registry)) {
                if (!(registry instanceof ForgeRegistry<IRecipe> nativeRegistry))
                    throw new IllegalStateException("Unsupported crafting registry implementation");
                registries.add(nativeRegistry);
            }
        }
        if (registries.isEmpty() || registries.getFirst() != RegistryManager.ACTIVE.getRegistry(IRecipe.class))
            throw new IllegalStateException("The active crafting registry is not available");
        return List.copyOf(registries);
    }

    static CraftingRegistryTransaction prepare(List<ForgeRegistry<IRecipe>> registries,
                                               Map<ResourceLocation, IRecipe> replacements) {
        var vanilla = RegistryManager.VANILLA.getRegistry(IRecipe.class);
        // VANILLA is the vanilla-only snapshot: it may receive replacements of names it already holds, never new ones.
        return prepare(registries, replacements,
                vanilla instanceof ForgeRegistry<IRecipe> mirror && registries.indexOf(mirror) > 0 ? mirror : null);
    }

    /** {@code existingOnly} is a mirror that only receives names it already contains (null for none). */
    static CraftingRegistryTransaction prepare(List<ForgeRegistry<IRecipe>> registries,
                                               Map<ResourceLocation, IRecipe> replacements,
                                               ForgeRegistry<IRecipe> existingOnly) {
        registries = List.copyOf(registries);
        if (registries.isEmpty()) throw new IllegalArgumentException("An active crafting registry is required");
        replacements = Collections.unmodifiableMap(new LinkedHashMap<>(replacements));
        registries.forEach(CraftingRegistryAccess::requireCrafting);
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        var locks = registries.stream().map(ForgeRegistry::isLocked).toList();
        List<ForgeRegistry<IRecipe>> drafts = new ArrayList<>();
        for (int i = 0; i < registries.size(); i++)
            drafts.add(CraftingRegistryAccess.copy(registries.get(i), before.get(i)));
        BitSet occupied = new BitSet();
        for (var registry : drafts) {
            occupied.or(CraftingRegistryAccess.availability(registry));
            CraftingRegistryAccess.blocked(registry).forEach(occupied::set);
        }
        int minimum = drafts.stream().mapToInt(CraftingRegistryAccess::minimum).max().orElseThrow();
        int maximum = drafts.stream().mapToInt(CraftingRegistryAccess::maximum).min().orElseThrow();
        for (var replacement : replacements.entrySet()) {
            ResourceLocation name = Objects.requireNonNull(replacement.getKey(), "recipe name");
            IRecipe previous = CraftingRegistryAccess.names(drafts.getFirst()).get(name);
            IRecipe recipe = replacement.getValue();
            if (recipe == null && previous == null) continue;
            if (recipe == null) recipe = new DisabledCraftingRecipe().setRegistryName(name);
            if (!name.equals(recipe.getRegistryName()))
                throw new IllegalArgumentException(
                        "Crafting recipe name does not match its requested identity: " + name);
            int id = previous == null ? occupied.nextClearBit(minimum) :
                    CraftingRegistryAccess.ids(drafts.getFirst()).inverse().get(previous);
            if (id > maximum) throw new IllegalStateException("Crafting registry has no available shared recipe ID");
            for (int index = 0; index < drafts.size(); index++) {
                var draft = drafts.get(index);
                if (index > 0 && registries.get(index) == existingOnly &&
                        !CraftingRegistryAccess.names(draft).containsKey(name))
                    continue;
                if (CraftingRegistryAccess.aliases(draft).containsKey(name))
                    throw new IllegalStateException("Use a crafting recipe's canonical name, not an alias: " + name);
                var existing = CraftingRegistryAccess.names(draft).get(name);
                var atId = CraftingRegistryAccess.ids(draft).get(id);
                if ((existing != null && draft.getID(existing) != id) ||
                        (atId != null && atId != existing) ||
                        (existing == null && CraftingRegistryAccess.availability(draft).get(id)))
                    throw new IllegalStateException("Crafting registry mirrors disagree about recipe ID " + id);
                CraftingRegistryAccess.put(draft, name, recipe, id);
            }
            occupied.set(id);
        }
        var originals = new ArrayList<Map<ResourceLocation, CraftingRegistryEntry>>();
        var expecteds = new ArrayList<Map<ResourceLocation, CraftingRegistryEntry>>();
        for (int i = 0; i < registries.size(); i++) {
            before.get(i).preserveExistingOrder(drafts.get(i));
            Map<ResourceLocation, CraftingRegistryEntry> original = new LinkedHashMap<>();
            Map<ResourceLocation, CraftingRegistryEntry> expected = new LinkedHashMap<>();
            for (var name : replacements.keySet()) {
                if (!CraftingRegistryAccess.names(drafts.getFirst()).containsKey(name)) continue;
                var recipe = CraftingRegistryAccess.names(drafts.get(i)).get(name);
                if (recipe == null) continue;
                int id = drafts.get(i).getID(recipe);
                original.put(name, CraftingRegistryEntry.capture(registries.get(i), name, id));
                expected.put(name, CraftingRegistryEntry.capture(drafts.get(i), name, id));
            }
            originals.add(original);
            expecteds.add(expected);
        }
        return new CraftingRegistryTransaction(registries, before,
                drafts.stream().map(CraftingRegistryImage::new).toList(), locks,
                new CraftingRegistryRestoration(registries, originals, expecteds));
    }

    CraftingRegistryTransaction(List<ForgeRegistry<IRecipe>> registries, List<CraftingRegistryImage> before,
                                List<CraftingRegistryImage> after, List<Boolean> locks,
                                CraftingRegistryRestoration restoration) {
        this.registries = registries;
        this.before = before;
        this.after = after;
        this.locks = locks;
        this.restoration = restoration;
        this.managedStages = registries.getFirst() == RegistryManager.ACTIVE.getRegistry(IRecipe.class);
    }

    public CraftingRegistryRestoration restoration() {
        requireThread();
        if (!published || restoration == null)
            throw new IllegalStateException("Only published replacement transactions provide an ownership baseline");
        return restoration;
    }

    CraftingRegistryRestoration preparedRestoration() {
        requireThread();
        if (consumed || restoration == null)
            throw new IllegalStateException("Only unconsumed replacement transactions provide a prepared baseline");
        return restoration;
    }

    public boolean isCurrent() {
        requireThread();
        return !consumed && matches(before);
    }

    public void publish() {
        requireThread();
        if (!isCurrent()) throw new IllegalStateException("Crafting registries changed during preparation");
        consumed = true;
        try {
            apply(after);
            if (!matches(after))
                throw new IllegalStateException("Crafting publication did not preserve the prepared state");
            published = true;
        } catch (RuntimeException failure) {
            try {
                apply(before);
            } catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
                throw new IllegalStateException("Crafting publication and restoration failed", failure);
            }
            throw new IllegalStateException("Crafting publication failed; original registries restored", failure);
        }
    }

    /** Aborts a published participant before other state commits; refuses to erase intervening registry changes. */
    public void restore() {
        requireThread();
        if (!published || !matches(after))
            throw new IllegalStateException("Crafting registries changed before restoration");
        apply(before);
        if (!matches(before))
            throw new IllegalStateException("Crafting restoration did not reproduce the original state");
        published = false;
    }

    private boolean matches(List<CraftingRegistryImage> expected) {
        if (managedStages && !registries.equals(currentRegistries())) return false;
        for (int i = 0; i < registries.size(); i++) {
            if (registries.get(i).isLocked() != locks.get(i) || !expected.get(i).matches(registries.get(i)))
                return false;
        }
        return true;
    }

    private void apply(List<CraftingRegistryImage> values) {
        for (int i = 0; i < registries.size(); i++) values.get(i).apply(registries.get(i));
    }

    private void requireThread() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("Crafting registration is thread confined");
    }
}

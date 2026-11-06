package gregtech.api.recipes;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selective baseline for an ownership coordinator. This is not itself an owner or reload adapter. */
@ApiStatus.Internal
public final class CraftingRegistryRestoration {

    private final List<ForgeRegistry<IRecipe>> registries;
    private final List<Map<ResourceLocation, CraftingRegistryEntry>> original;
    private final List<Map<ResourceLocation, CraftingRegistryEntry>> expected;
    private final boolean managedStages;

    CraftingRegistryRestoration(List<ForgeRegistry<IRecipe>> registries,
                                List<Map<ResourceLocation, CraftingRegistryEntry>> original,
                                List<Map<ResourceLocation, CraftingRegistryEntry>> expected) {
        this(registries, original, expected,
                registries.getFirst() == RegistryManager.ACTIVE.getRegistry(IRecipe.class));
    }

    private CraftingRegistryRestoration(List<ForgeRegistry<IRecipe>> registries,
                                        List<Map<ResourceLocation, CraftingRegistryEntry>> original,
                                        List<Map<ResourceLocation, CraftingRegistryEntry>> expected,
                                        boolean managedStages) {
        this.registries = List.copyOf(registries);
        this.original = original.stream().map(CraftingRegistryRestoration::snapshot).toList();
        this.expected = expected.stream().map(CraftingRegistryRestoration::snapshot).toList();
        this.managedStages = managedStages;
    }

    /** Stages restoration of only the captured names, preserving unrelated current entries and lock states. */
    public CraftingRegistryTransaction prepare() {
        var current = withFrozenStage();
        if (current != this) return current.prepare();
        verifyCurrent();
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        var locks = registries.stream().map(ForgeRegistry::isLocked).toList();
        List<ForgeRegistry<IRecipe>> drafts = new ArrayList<>();
        for (int i = 0; i < registries.size(); i++)
            drafts.add(CraftingRegistryAccess.copy(registries.get(i), before.get(i)));
        for (int i = 0; i < drafts.size(); i++) {
            for (var entry : original.get(i).entrySet())
                entry.getValue().restore(drafts.get(i), expected.get(i).get(entry.getKey()));
            before.get(i).preserveExistingOrder(drafts.get(i));
        }
        return new CraftingRegistryTransaction(registries, before,
                drafts.stream().map(CraftingRegistryImage::new).toList(), locks, null);
    }

    List<ResourceLocation> names() {
        return List.copyOf(expected.getFirst().keySet());
    }

    boolean retains(ResourceLocation name, IRecipe recipe) {
        var entry = expected.getFirst().get(name);
        return entry != null && (recipe == null ? entry.recipe() instanceof DisabledCraftingRecipe :
                entry.recipe() == recipe);
    }

    boolean isCurrent() {
        try {
            withFrozenStage().verifyCurrent();
            return true;
        } catch (IllegalStateException changed) {
            return false;
        }
    }

    void verifyCurrent() {
        requireStages();
        for (int i = 0; i < registries.size(); i++) {
            for (var entry : expected.get(i).values()) {
                if (!entry.matches(registries.get(i)))
                    throw new IllegalStateException("Owned crafting entry changed: " + entry.name());
            }
        }
    }

    /** Carries the first baseline through a later replacement; overlapping names must form an exact chain. */
    public CraftingRegistryRestoration then(CraftingRegistryRestoration next) {
        var preceding = withFrozenStage();
        var following = next.withFrozenStage();
        if (preceding != this || following != next) return preceding.then(following);
        requireStages();
        next.requireStages();
        if (managedStages != next.managedStages || !registries.equals(next.registries))
            throw new IllegalStateException("Crafting registry views changed between owned replacements");
        var originals = new ArrayList<Map<ResourceLocation, CraftingRegistryEntry>>();
        var expecteds = new ArrayList<Map<ResourceLocation, CraftingRegistryEntry>>();
        for (int i = 0; i < registries.size(); i++) {
            Map<ResourceLocation, CraftingRegistryEntry> baseline = new LinkedHashMap<>(original.get(i));
            Map<ResourceLocation, CraftingRegistryEntry> current = new LinkedHashMap<>(expected.get(i));
            for (var entry : next.original.get(i).entrySet()) {
                var previous = current.get(entry.getKey());
                if (previous != null && !previous.sameState(entry.getValue()))
                    throw new IllegalStateException(
                            "Crafting replacements do not form an owned chain: " + entry.getKey());
                baseline.putIfAbsent(entry.getKey(), entry.getValue());
                current.put(entry.getKey(), next.expected.get(i).get(entry.getKey()));
            }
            originals.add(baseline);
            expecteds.add(current);
        }
        return new CraftingRegistryRestoration(registries, originals, expecteds, managedStages);
    }

    CraftingRegistryRestoration withFrozenStage() {
        if (!managedStages) return this;
        var current = CraftingRegistryTransaction.currentRegistries();
        if (registries.equals(current)) {
            var activeNames = CraftingRegistryAccess.names(registries.getFirst());
            if (expected.getFirst().values().stream()
                    .anyMatch(entry -> activeNames.get(entry.name()) != entry.recipe()))
                return this;
            try {
                return resyncActive();
            } catch (IllegalStateException notNativeCopy) {
                // A preceding baseline can already be superseded while then() checks the exact ownership chain.
                // Verification/restoration still rejects a current entry that differs from its expected state.
                return this;
            }
        }
        var frozen = RegistryManager.FROZEN.getRegistry(IRecipe.class);
        if (!(frozen instanceof ForgeRegistry<IRecipe> nativeFrozen))
            throw new IllegalStateException("A native FROZEN crafting registry is required for stage adoption");
        return adoptFrozen(current, nativeFrozen);
    }

    CraftingRegistryRestoration adoptFrozen(List<ForgeRegistry<IRecipe>> current,
                                            ForgeRegistry<IRecipe> frozen) {
        if (frozen == null || registries.contains(frozen) || current.size() != registries.size() + 1 ||
                current.size() < 2 || current.get(1) != frozen ||
                !current.stream().filter(registry -> registry != frozen).toList().equals(registries))
            throw new IllegalStateException("Only a newly copied FROZEN crafting registry can be adopted");
        CraftingRegistryAccess.requireCrafting(frozen);
        Map<ResourceLocation, CraftingRegistryEntry> frozenOriginal = new LinkedHashMap<>();
        Map<ResourceLocation, CraftingRegistryEntry> frozenExpected = new LinkedHashMap<>();
        for (var entry : expected.getFirst().values()) {
            var copied = CraftingRegistryEntry.capture(frozen, entry.name(), entry.id());
            if (!entry.matchesFrozenCopy(copied))
                throw new IllegalStateException(
                        "Frozen crafting entry is not the expected native copy: " + entry.name());
            frozenExpected.put(entry.name(), copied);
            frozenOriginal.put(entry.name(), original.getFirst().get(entry.name()).originalForFrozen(copied));
        }
        var originals = new ArrayList<>(original);
        var expecteds = new ArrayList<>(expected);
        originals.add(1, frozenOriginal);
        expecteds.add(1, frozenExpected);
        var adopted = new CraftingRegistryRestoration(current, originals, expecteds, managedStages).resyncActive();
        adopted.verifyCurrent();
        return adopted;
    }

    /** Native world loading can sync ACTIVE from FROZEN and recreate its owner keys. */
    CraftingRegistryRestoration resyncActive() {
        var active = registries.getFirst();
        if (expected.getFirst().values().stream().allMatch(entry -> entry.matches(active))) return this;
        if (registries.size() < 3)
            throw new IllegalStateException("A frozen crafting baseline is required for native resync");
        for (int i = 1; i < registries.size(); i++) {
            for (var entry : expected.get(i).values()) {
                if (!entry.matches(registries.get(i)))
                    throw new IllegalStateException("Owned crafting reference view changed: " + entry.name());
            }
        }
        Map<ResourceLocation, CraftingRegistryEntry> activeOriginal = new LinkedHashMap<>();
        Map<ResourceLocation, CraftingRegistryEntry> activeExpected = new LinkedHashMap<>();
        for (var entry : expected.getFirst().values()) {
            var copied = CraftingRegistryEntry.capture(active, entry.name(), entry.id());
            if (!entry.matches(active) && !entry.matchesFrozenCopy(copied))
                throw new IllegalStateException("Owned crafting entry is not a native resync: " + entry.name());
            activeExpected.put(entry.name(), copied);
            activeOriginal.put(entry.name(), entry.matches(active) ? original.getFirst().get(entry.name()) :
                    original.getFirst().get(entry.name()).originalForFrozen(copied));
        }
        var originals = new ArrayList<>(original);
        var expecteds = new ArrayList<>(expected);
        originals.set(0, activeOriginal);
        expecteds.set(0, activeExpected);
        return new CraftingRegistryRestoration(registries, originals, expecteds, managedStages);
    }

    private void requireStages() {
        if (managedStages && !registries.equals(CraftingRegistryTransaction.currentRegistries()))
            throw new IllegalStateException("Crafting registry views changed while an ownership baseline was retained");
    }

    private static <K, V> Map<K, V> snapshot(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

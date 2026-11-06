package gregtech.api.unification;

import gregtech.api.recipes.NativeRecipeRegistration;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecyclingContribution;

import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Optional registration-thread capture; no builder, script closure or per-tick observer is retained. */
public final class NativeRecyclingCapture implements AutoCloseable {

    private final RecyclingManager manager;
    private final Thread thread = Thread.currentThread();
    private final List<RecyclingCaptureEntry> entries = new ArrayList<>();
    private boolean closed;

    NativeRecyclingCapture(RecyclingManager manager) {
        this.manager = manager;
    }

    void requireOpenThread() {
        requireThread();
        if (closed) throw new IllegalStateException("Native recycling capture is closed");
    }

    private void requireThread() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("Native recycling capture requires its registration thread");
    }

    void record(NativeRecipeRegistration registration, RecyclingContribution contribution) {
        requireOpenThread();
        entries.add(new CapturedMachineRecycling(Objects.requireNonNull(registration),
                Objects.requireNonNull(contribution)));
    }

    void record(IRecipe recipe, RecyclingContribution contribution) {
        requireOpenThread();
        entries.add(
                new CapturedCraftingRecycling(Objects.requireNonNull(recipe), Objects.requireNonNull(contribution)));
    }

    /**
     * Current accepted declarations in registration order. Removed/replaced identities are excluded, including
     * equal-looking replacement recipes. The returned list and contribution values are detached from this collector.
     * Call while registration is quiescent; this is a snapshot, not a reload subscription.
     */
    public List<RecyclingContribution> snapshot() {
        requireThread();
        Map<RecipeMap<?>, Set<Recipe>> accepted = new IdentityHashMap<>();
        List<RecyclingContribution> result = new ArrayList<>();
        for (var entry : entries) {
            boolean present = switch (entry) {
                case CapturedMachineRecycling machine -> accepted.computeIfAbsent(machine.registration().map(), map -> {
                    Set<Recipe> recipes = Collections.newSetFromMap(new IdentityHashMap<>());
                    recipes.addAll(map.getRecipeList());
                    return recipes;
                }).contains(machine.registration().recipe());
                case CapturedCraftingRecycling crafting -> crafting.recipe().getRegistryName() != null &&
                        ForgeRegistries.RECIPES.getValue(crafting.recipe().getRegistryName()) == crafting.recipe();
            };
            if (present) result.add(entry.contribution());
        }
        return List.copyOf(result);
    }

    @Override
    public void close() {
        requireThread();
        if (!closed) {
            manager.finishNativeCapture(this);
            closed = true;
        }
    }
}

package gregtech.api.unification;

import gregtech.api.recipes.NativeRecipeRegistration;
import gregtech.api.recipes.RecyclingContribution;
import gregtech.api.recipes.RecyclingOwnershipRegistry;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.api.util.GTUtility;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiConsumer;

public final class RecyclingManager {

    private Map<ItemAndMetadata, RecyclingData> map = new Object2ObjectOpenHashMap<>();
    private long revision;
    private final RecyclingOwnershipRegistry ownership = new RecyclingOwnershipRegistry(this);
    private NativeRecyclingCapture nativeCapture;

    /** Start before native recipe registration, then close after taking the required snapshots. */
    public NativeRecyclingCapture beginNativeCapture() {
        if (nativeCapture != null) throw new IllegalStateException("Native recycling capture is already active");
        nativeCapture = new NativeRecyclingCapture(this);
        return nativeCapture;
    }

    /** Also checks the registration thread before callers admit any recipe. */
    public boolean isNativeCaptureActive() {
        if (nativeCapture == null) return false;
        nativeCapture.requireOpenThread();
        return true;
    }

    public void captureNativeRecipe(NativeRecipeRegistration registration, RecyclingContribution contribution) {
        if (nativeCapture != null) nativeCapture.record(registration, contribution);
    }

    public void captureNativeRecipe(IRecipe recipe, RecyclingContribution contribution) {
        if (nativeCapture != null) nativeCapture.record(recipe, contribution);
    }

    void finishNativeCapture(NativeRecyclingCapture capture) {
        if (nativeCapture != capture) throw new IllegalStateException("Native recycling capture owner changed");
        nativeCapture = null;
    }

    /** Shared preparation and ownership for native and typed recycling contributions. */
    public RecyclingOwnershipRegistry ownership() {
        return ownership;
    }

    /**
     * @param stack the stack to give recycling data
     * @param data  the recycling data
     */
    public void registerRecyclingData(@NotNull ItemStack stack, @NotNull RecyclingData data) {
        if (stack.isEmpty()) {
            return;
        }
        registerRecyclingData(new ItemAndMetadata(stack), data);
    }

    /**
     * @param key  the key to give recycling data
     * @param data the recycling data
     */
    public void registerRecyclingData(@NotNull ItemAndMetadata key,
                                      @NotNull RecyclingData data) {
        map.put(key, data);
        revision++;
    }

    /**
     * @param stack the stack
     * @return the recycling data associated with the stack
     */
    public @Nullable RecyclingData getRecyclingData(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return getRecyclingData(new ItemAndMetadata(stack));
    }

    /**
     * @param key the key
     * @return the recycling data associated with the key
     */
    public @Nullable RecyclingData getRecyclingData(@NotNull ItemAndMetadata key) {
        return GTUtility.getOrWildcardMeta(map, key);
    }

    /**
     * @param stack the stack whose data should be removed
     */
    public void removeRecyclingData(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        removeRecyclingData(new ItemAndMetadata(stack));
    }

    /**
     * @param key the key whose data should be removed
     */
    public void removeRecyclingData(@NotNull ItemAndMetadata key) {
        map.remove(key);
        revision++;
    }

    /**
     * Iterate all registered recycling entries
     *
     * @param action the action to apply to each entry
     */
    public void iterate(@NotNull BiConsumer<ItemAndMetadata, RecyclingData> action) {
        map.forEach(action);
    }

    /** Exact entries, without wildcard fallback, for quiescent registration transactions. */
    public Map<ItemAndMetadata, RecyclingData> snapshot() {
        return Map.copyOf(map);
    }

    public long revision() {
        return revision;
    }

    /** Null replacement values remove exact entries. No mutation occurs during preparation. */
    public PreparedChange prepare(long expectedRevision, Map<ItemAndMetadata, RecyclingData> replacements) {
        if (expectedRevision != revision)
            throw new IllegalStateException("Recycling registry changed during preparation");
        Map<ItemAndMetadata, RecyclingData> staged = new Object2ObjectOpenHashMap<>(map);
        replacements.forEach((key, data) -> {
            if (data == null) staged.remove(key);
            else staged.put(key, data);
        });
        return new PreparedChange(expectedRevision, staged);
    }

    public final class PreparedChange {

        private final long expectedRevision;
        private final Map<ItemAndMetadata, RecyclingData> staged;

        private PreparedChange(long expectedRevision, Map<ItemAndMetadata, RecyclingData> staged) {
            this.expectedRevision = expectedRevision;
            this.staged = staged;
        }

        public boolean isCurrent() {
            return revision == expectedRevision;
        }

        /** Call on the quiescent registration thread before publishing the other prevalidated registry states. */
        public void publish() {
            if (!isCurrent()) throw new IllegalStateException("Recycling registry changed before publication");
            map = staged;
            revision++;
        }
    }
}

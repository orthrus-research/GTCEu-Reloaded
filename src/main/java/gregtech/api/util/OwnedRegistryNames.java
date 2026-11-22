package gregtech.api.util;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.registries.IForgeRegistryEntry;

import org.jetbrains.annotations.NotNull;

/**
 * Names registry entries that GregTech builds on behalf of another mod.
 * <p>
 * GregTech creates blocks, items and recipes in the namespace of each material and MTE registry, so
 * an addon registry {@code addon} gets {@code addon:meta_dust}, {@code addon:wire_single} and so on.
 * GregTech builds them during its own lifecycle events, while FML's active mod is {@code gregtech}.
 * FML compares every new registry name with the active mod and logs "Potentially Dangerous
 * alternative prefix" for each mismatch, which buries real warnings under one line per object.
 * <p>
 * {@link #set} makes the namespace's own mod active while the name is set, so FML sees the owner it
 * expects. The name, the entry and the registry FML later puts it in are unchanged. Names in the
 * {@code minecraft} namespace (GregTech's deliberate vanilla overrides) and namespaces that belong to
 * no loaded mod keep FML's check as before.
 */
public final class OwnedRegistryNames {

    private OwnedRegistryNames() {}

    public static <V extends IForgeRegistryEntry<V>> V set(@NotNull IForgeRegistryEntry<V> entry,
                                                          @NotNull String namespace, @NotNull String path) {
        return set(entry, new ResourceLocation(namespace, path));
    }

    public static <V extends IForgeRegistryEntry<V>> V set(@NotNull IForgeRegistryEntry<V> entry,
                                                          @NotNull ResourceLocation name) {
        Loader loader = Loader.instance();
        ModContainer active = loader.activeModContainer();
        ModContainer owner = "minecraft".equals(name.getNamespace()) ? null :
                loader.getIndexedModList().get(name.getNamespace());
        if (owner == null || owner == active) return entry.setRegistryName(name);
        loader.setActiveModContainer(owner);
        try {
            return entry.setRegistryName(name);
        } finally {
            loader.setActiveModContainer(active);
        }
    }
}

package gregtech.api.recipes;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.RegistryManager;

import com.google.common.collect.BiMap;
import com.google.common.collect.Multimap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;

/** Pinned Cleanroom registry access, used only while preparing or publishing configuration. */
final class CraftingRegistryAccess {

    private static final MethodHandles.Lookup LOOKUP;
    private static final MethodHandle COPY;
    private static final MethodHandle ADD;
    private static final VarHandle NAMES;
    private static final VarHandle IDS;
    private static final VarHandle OWNERS;
    private static final VarHandle AVAILABLE;
    private static final VarHandle DUMMIES;
    private static final VarHandle ALIASES;
    private static final VarHandle OVERRIDES;
    private static final VarHandle BLOCKED;
    private static final VarHandle STAGE;
    private static final VarHandle MIN;
    private static final VarHandle MAX;
    private static final VarHandle DELEGATED;
    private static final VarHandle ADD_CALLBACK;
    private static final VarHandle DEFAULT_KEY;
    private static final VarHandle OWNER_NAMESPACE;
    private static final VarHandle OWNER_KEY;

    static {
        try {
            LOOKUP = MethodHandles.privateLookupIn(ForgeRegistry.class, MethodHandles.lookup());
            COPY = LOOKUP.findVirtual(ForgeRegistry.class, "copy",
                    MethodType.methodType(ForgeRegistry.class, RegistryManager.class));
            ADD = LOOKUP.findVirtual(ForgeRegistry.class, "add",
                    MethodType.methodType(int.class, int.class, IForgeRegistryEntry.class, String.class));
            NAMES = field("names", BiMap.class);
            IDS = field("ids", BiMap.class);
            OWNERS = field("owners", BiMap.class);
            AVAILABLE = field("availabilityMap", BitSet.class);
            DUMMIES = field("dummies", Set.class);
            ALIASES = field("aliases", Map.class);
            OVERRIDES = field("overrides", Multimap.class);
            BLOCKED = field("blocked", Set.class);
            STAGE = field("stage", RegistryManager.class);
            MIN = field("min", int.class);
            MAX = field("max", int.class);
            DELEGATED = field("isDelegated", boolean.class);
            ADD_CALLBACK = field("add", IForgeRegistry.AddCallback.class);
            DEFAULT_KEY = field("defaultKey", ResourceLocation.class);
            Class<?> owner = Class.forName(ForgeRegistry.class.getName() + "$OverrideOwner");
            var ownerLookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            OWNER_NAMESPACE = ownerLookup.findVarHandle(owner, "owner", String.class);
            OWNER_KEY = ownerLookup.findVarHandle(owner, "key", ResourceLocation.class);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private CraftingRegistryAccess() {}

    static boolean isDefaultOwner(Object owner, ResourceLocation name) {
        return owner != null && name.getNamespace().equals(OWNER_NAMESPACE.get(owner)) &&
                name.equals(OWNER_KEY.get(owner));
    }

    private static VarHandle field(String name, Class<?> type) throws ReflectiveOperationException {
        return LOOKUP.findVarHandle(ForgeRegistry.class, name, type);
    }

    static void requireCrafting(ForgeRegistry<IRecipe> registry) {
        if (registry.getClass() != ForgeRegistry.class || registry.getRegistrySuperType() != IRecipe.class ||
                (boolean) DELEGATED.get(registry) || ADD_CALLBACK.get(registry) != null ||
                DEFAULT_KEY.get(registry) != null) {
            throw new IllegalStateException("Crafting transactions require Cleanroom's nondelegated recipe registry");
        }
    }

    static ForgeRegistry<IRecipe> copy(ForgeRegistry<IRecipe> registry) {
        return copy(registry, new CraftingRegistryImage(registry));
    }

    @SuppressWarnings("unchecked")
    static ForgeRegistry<IRecipe> copy(ForgeRegistry<IRecipe> registry, CraftingRegistryImage image) {
        requireCrafting(registry);
        try {
            var copy = (ForgeRegistry<IRecipe>) COPY.invoke(registry, (RegistryManager) STAGE.get(registry));
            image.apply(copy);
            return copy;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not prepare a detached crafting registry", failure);
        }
    }

    static void put(ForgeRegistry<IRecipe> draft, ResourceLocation name, IRecipe recipe, int id) {
        IRecipe old = names(draft).remove(name);
        Object owner = old == null ? null : owners(draft).inverse().remove(old);
        if (old != null) ids(draft).inverse().remove(old);
        IRecipe occupied = ids(draft).get(id);
        if (occupied != null) throw new IllegalStateException("Crafting ID is occupied by another recipe: " + id);
        availability(draft).clear(id);
        try {
            int assigned = (int) ADD.invoke(draft, id, (IForgeRegistryEntry<?>) recipe, name.getNamespace());
            if (assigned != id) throw new IllegalStateException("Crafting ID changed during preparation");
            if (owner != null) {
                owners(draft).inverse().remove(recipe);
                owners(draft).put(owner, recipe);
            }
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not prepare crafting registration: " + name, failure);
        }
    }

    @SuppressWarnings("unchecked")
    static BiMap<ResourceLocation, IRecipe> names(ForgeRegistry<IRecipe> registry) {
        return (BiMap<ResourceLocation, IRecipe>) NAMES.get(registry);
    }

    @SuppressWarnings("unchecked")
    static BiMap<Integer, IRecipe> ids(ForgeRegistry<IRecipe> registry) {
        return (BiMap<Integer, IRecipe>) IDS.get(registry);
    }

    @SuppressWarnings("unchecked")
    static BiMap<Object, IRecipe> owners(ForgeRegistry<IRecipe> registry) {
        return (BiMap<Object, IRecipe>) OWNERS.get(registry);
    }

    static BitSet availability(ForgeRegistry<IRecipe> registry) {
        return (BitSet) AVAILABLE.get(registry);
    }

    @SuppressWarnings("unchecked")
    static Set<ResourceLocation> dummies(ForgeRegistry<IRecipe> registry) {
        return (Set<ResourceLocation>) DUMMIES.get(registry);
    }

    @SuppressWarnings("unchecked")
    static Map<ResourceLocation, ResourceLocation> aliases(ForgeRegistry<IRecipe> registry) {
        return (Map<ResourceLocation, ResourceLocation>) ALIASES.get(registry);
    }

    @SuppressWarnings("unchecked")
    static Multimap<ResourceLocation, IRecipe> overrides(ForgeRegistry<IRecipe> registry) {
        return (Multimap<ResourceLocation, IRecipe>) OVERRIDES.get(registry);
    }

    @SuppressWarnings("unchecked")
    static Set<Integer> blocked(ForgeRegistry<IRecipe> registry) {
        return (Set<Integer>) BLOCKED.get(registry);
    }

    static int minimum(ForgeRegistry<IRecipe> registry) {
        return (int) MIN.get(registry);
    }

    static int maximum(ForgeRegistry<IRecipe> registry) {
        return (int) MAX.get(registry);
    }
}

package gregtech;

import gregtech.api.GregTechAPI;
import gregtech.api.block.ICleanroomFilter;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IBatteryData;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.util.GTControlledRegistry;
import gregtech.common.ConfigHolder;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.IntIdentityHashBiMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.util.registry.RegistrySimple;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shared game state tests change and must give back: {@link ConfigHolder} values, the recipe map and recipe
 * category registries with each map's recipes, the MTE registries with their machines, the meta items with their
 * values, the multiblock abilities and the coil, battery and cleanroom filter states.
 * <p>
 * {@link #take()} records it; {@link #restore()} puts it back however the test changed it: a map a test registered
 * under a real map's name gives way to the real map again, recipes it added are removed, registries it created are
 * dropped, machines it registered are unregistered and meta item values it added are removed.
 * {@link GameStateExtension} does this around every test class and test.
 */
public final class GameStateSnapshot {

    private static final Field RECIPE_MAP_REGISTRY = field(RecipeMap.class, "RECIPE_MAP_REGISTRY");
    private static final Field CATEGORIES = field(GTRecipeCategory.class, "categories");
    private static final Field MTE_REGISTRY_MAP = field(MTEManager.class, "registryMap");
    private static final Field MTE_NETWORK_MAP = field(MTEManager.class, "networkMap");
    private static final Field MTE_NETWORK_ID = field(MTEManager.class, "networkId");
    private static final Field MTE_BLOCK = field(MTERegistry.class, "block");
    private static final Field REGISTRY_FROZEN = field(GTControlledRegistry.class, "frozen");
    private static final Field REGISTRY_OBJECTS = field(RegistrySimple.class, "registryObjects");
    private static final Field REGISTRY_VALUES = field(RegistrySimple.class, "values");
    private static final Field INVERSE_OBJECTS = field(RegistryNamespaced.class, "inverseObjectRegistry");
    private static final Field INTEGER_MAP = field(RegistryNamespaced.class, "underlyingIntegerMap");
    private static final Field META_ITEMS = field(MetaItem.class, "META_ITEMS");
    private static final Field META_ITEM_VALUES = field(MetaItem.class, "metaItems");
    private static final Field META_ITEM_NAMES = field(MetaItem.class, "names");

    private final List<Runnable> config = new ArrayList<>();
    private final Map<String, RecipeMap<?>> recipeMaps;
    private final Map<String, GTRecipeCategory> categories;
    private final Map<RecipeMap<?>, List<Recipe>> recipes = new IdentityHashMap<>();
    private final @Nullable MTEManager mteManager;
    private final Map<String, MTERegistry> mteRegistries = new LinkedHashMap<>();
    private final Map<Integer, MTERegistry> mteNetworkIds = new LinkedHashMap<>();
    private final int mteNetworkId;
    private final List<MachineRegistryContents> mteContents = new ArrayList<>();
    private final List<MetaItem<?>> metaItems;
    private final Map<MetaItem<?>, Map<Short, Object>> metaItemValues = new IdentityHashMap<>();
    private final Map<MetaItem<?>, Map<String, Object>> metaItemNames = new IdentityHashMap<>();
    private final Map<MultiblockAbility<?>, List<MetaTileEntity>> abilities = new LinkedHashMap<>();
    private final Map<IBlockState, IHeatingCoilBlockStats> coils;
    private final Map<IBlockState, IBatteryData> batteries;
    private final Map<IBlockState, ICleanroomFilter> filters;

    private GameStateSnapshot() {
        captureConfig(ConfigHolder.class, null, Collections.newSetFromMap(new IdentityHashMap<>()));

        this.recipeMaps = new LinkedHashMap<>(recipeMapRegistry());
        this.categories = new LinkedHashMap<>(categoryRegistry());
        for (RecipeMap<?> map : recipeMaps.values()) {
            recipes.put(map, List.copyOf(map.getRecipeList()));
        }

        this.mteManager = GregTechAPI.mteManager;
        this.mteNetworkId = (int) read(MTE_NETWORK_ID, null);
        if (mteManager != null) {
            mteRegistries.putAll(registryMap(mteManager));
            mteNetworkIds.putAll(networkMap(mteManager));
            for (MTERegistry registry : mteRegistries.values()) {
                mteContents.add(new MachineRegistryContents(registry));
            }
        }

        this.metaItems = List.copyOf(metaItemList());
        for (MetaItem<?> item : metaItems) {
            metaItemValues.put(item, new LinkedHashMap<>(metaItemValues(item)));
            metaItemNames.put(item, new LinkedHashMap<>(metaItemNames(item)));
        }

        MultiblockAbility.REGISTRY.forEach((ability, machines) -> abilities.put(ability, List.copyOf(machines)));
        this.coils = new LinkedHashMap<>(GregTechAPI.HEATING_COILS);
        this.batteries = new LinkedHashMap<>(GregTechAPI.PSS_BATTERIES);
        this.filters = new LinkedHashMap<>(GregTechAPI.CLEANROOM_FILTERS);
    }

    /** @return the shared game state as it is now */
    public static GameStateSnapshot take() {
        return new GameStateSnapshot();
    }

    /** Puts the recorded state back. */
    public void restore() {
        config.forEach(Runnable::run);

        restoreMap(recipeMapRegistry(), recipeMaps);
        restoreMap(categoryRegistry(), categories);
        recipes.forEach(GameStateSnapshot::restoreRecipes);

        GregTechAPI.mteManager = mteManager;
        write(MTE_NETWORK_ID, null, mteNetworkId);
        if (mteManager != null) {
            restoreMap(registryMap(mteManager), mteRegistries);
            restoreMap(networkMap(mteManager), mteNetworkIds);
            mteContents.forEach(MachineRegistryContents::restore);
        }

        restoreList(metaItemList(), metaItems);
        metaItemValues.forEach((item, values) -> restoreMap(metaItemValues(item), values));
        metaItemNames.forEach((item, names) -> restoreMap(metaItemNames(item), names));

        MultiblockAbility.REGISTRY.clear();
        abilities.forEach((ability, machines) -> MultiblockAbility.REGISTRY.put(ability, new ArrayList<>(machines)));
        restoreMap(GregTechAPI.HEATING_COILS, coils);
        restoreMap(GregTechAPI.PSS_BATTERIES, batteries);
        restoreMap(GregTechAPI.CLEANROOM_FILTERS, filters);
    }

    private static <K, V> void restoreMap(Map<K, V> target, Map<K, V> saved) {
        target.clear();
        target.putAll(saved);
    }

    private static <T> void restoreList(List<T> target, List<T> saved) {
        target.clear();
        target.addAll(saved);
    }

    /** Removes the recipes a test added to the map and gives back the ones it removed. */
    private static void restoreRecipes(RecipeMap<?> map, List<Recipe> saved) {
        Set<Recipe> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        keep.addAll(saved);
        Set<Recipe> present = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Recipe recipe : List.copyOf(map.getRecipeList())) {
            if (keep.contains(recipe)) {
                present.add(recipe);
            } else if (!map.removeRecipe(recipe)) {
                throw new IllegalStateException("Could not remove a test's recipe from " + map.unlocalizedName);
            }
        }
        for (Recipe recipe : saved) {
            if (!present.contains(recipe) && !map.compileRecipe(recipe)) {
                throw new IllegalStateException("Could not give a recipe back to " + map.unlocalizedName);
            }
        }
    }

    /**
     * Records every assignable field of the configuration tree: the option objects {@link ConfigHolder} holds, and
     * the option objects nested in them. An array field gets its own array back with its recorded elements.
     */
    private void captureConfig(Class<?> type, @Nullable Object owner, Set<Object> seen) {
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) != (owner == null) || field.isSynthetic()) continue;
            field.setAccessible(true);
            Object value = read(field, owner);
            if (!Modifier.isFinal(field.getModifiers())) {
                Object contents = copy(value);
                config.add(() -> {
                    if (contents != value) System.arraycopy(contents, 0, value, 0, Array.getLength(value));
                    write(field, owner, value);
                });
            }
            if (value != null && isOptionObject(value.getClass()) && seen.add(value)) {
                captureConfig(value.getClass(), value, seen);
            }
        }
    }

    private static boolean isOptionObject(Class<?> type) {
        for (Class<?> outer = type.getEnclosingClass(); outer != null; outer = outer.getEnclosingClass()) {
            if (outer == ConfigHolder.class) return true;
        }
        return false;
    }

    private static @Nullable Object copy(@Nullable Object value) {
        if (value == null || !value.getClass().isArray()) return value;
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().getComponentType(), length);
        System.arraycopy(value, 0, copy, 0, length);
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, RecipeMap<?>> recipeMapRegistry() {
        return (Map<String, RecipeMap<?>>) read(RECIPE_MAP_REGISTRY, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, GTRecipeCategory> categoryRegistry() {
        return (Map<String, GTRecipeCategory>) read(CATEGORIES, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, MTERegistry> registryMap(MTEManager manager) {
        return (Map<String, MTERegistry>) read(MTE_REGISTRY_MAP, manager);
    }

    @SuppressWarnings("unchecked")
    private static Int2ObjectMap<MTERegistry> networkMap(MTEManager manager) {
        return (Int2ObjectMap<MTERegistry>) read(MTE_NETWORK_MAP, manager);
    }

    @SuppressWarnings("unchecked")
    private static List<MetaItem<?>> metaItemList() {
        return (List<MetaItem<?>>) read(META_ITEMS, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Short, Object> metaItemValues(MetaItem<?> item) {
        return (Map<Short, Object>) read(META_ITEM_VALUES, item);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metaItemNames(MetaItem<?> item) {
        return (Map<String, Object>) read(META_ITEM_NAMES, item);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("The game state snapshot no longer finds " + owner.getName() + '.' + name,
                    e);
        }
    }

    private static Object read(Field field, @Nullable Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Field field, @Nullable Object owner, @Nullable Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One MTE registry's machines with their ids, its block and whether it was frozen. */
    private static final class MachineRegistryContents {

        private final MTERegistry registry;
        private final Map<ResourceLocation, MetaTileEntity> machines = new LinkedHashMap<>();
        private final Map<MetaTileEntity, Integer> ids = new IdentityHashMap<>();
        private final Object block;
        private final boolean frozen;

        MachineRegistryContents(MTERegistry registry) {
            this.registry = registry;
            for (ResourceLocation key : registry.getKeys()) {
                MetaTileEntity machine = registry.getObject(key);
                machines.put(key, machine);
                ids.put(machine, registry.getIDForObject(machine));
            }
            this.block = read(MTE_BLOCK, registry);
            this.frozen = registry.isFrozen();
        }

        @SuppressWarnings("unchecked")
        void restore() {
            var objects = (Map<ResourceLocation, MetaTileEntity>) read(REGISTRY_OBJECTS, registry);
            var inverse = (Map<MetaTileEntity, ResourceLocation>) read(INVERSE_OBJECTS, registry);
            var integers = (IntIdentityHashBiMap<MetaTileEntity>) read(INTEGER_MAP, registry);
            objects.clear();
            inverse.clear();
            integers.clear();
            write(REGISTRY_VALUES, registry, null);
            machines.forEach((key, machine) -> {
                objects.put(key, machine);
                // a namespaced registry's inverse map is a view of its object map; put it only where it is not
                if (!inverse.containsKey(machine)) inverse.put(machine, key);
                integers.put(machine, ids.get(machine));
            });
            write(MTE_BLOCK, registry, block);
            write(REGISTRY_FROZEN, registry, frozen);
        }
    }
}

package gregtech.api;

import gregtech.api.advancement.IAdvancementManager;
import gregtech.api.block.ICleanroomFilter;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.block.coil.CoilManager;
import gregtech.api.command.ICommandManager;
import gregtech.api.config.GTConfig;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.event.HighTierEvent;
import gregtech.api.metatileentity.multiblock.IBatteryData;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.modules.IModuleManager;
import gregtech.api.network.INetworkHandler;
import gregtech.api.recipes.properties.RecipePropertyRegistry;
import gregtech.api.sound.ISoundManager;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.api.unification.material.registry.MarkerMaterialRegistry;
import gregtech.api.unification.ore.StoneType;
import gregtech.api.util.GTControlledRegistry;
import gregtech.api.util.GTLog;
import gregtech.api.util.IBlockOre;
import gregtech.datafix.migration.lib.MigrationAPI;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.GenericEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GregTechAPI {

    /** Set by GTCEu's mod constructor, before any lifecycle event. */
    public static Object instance;
    /**
     * Set during GTCEu's construction ({@code FMLConstructionEvent}), before its modules are created.
     * {@link #moduleManager()} reads it and fails clearly when it is not set yet.
     */
    public static IModuleManager moduleManager;
    /**
     * Set during GTCEu's construction ({@code FMLConstructionEvent}), when the core module is created.
     * {@link #networkHandler()} reads it and fails clearly when it is not set yet.
     */
    public static INetworkHandler networkHandler;
    /**
     * Set when a server starts ({@code FMLServerStartingEvent}) and kept afterwards; never set during mod loading,
     * so add commands from a server-starting handler. {@link #commandManager()} reads it and fails clearly when it
     * is not set yet.
     */
    public static ICommandManager commandManager;
    /**
     * Set during GTCEu's pre-initialization ({@code FMLPreInitializationEvent}).
     * {@link #advancementManager()} reads it and fails clearly when it is not set yet.
     */
    public static IAdvancementManager advancementManager;
    /**
     * Set during GTCEu's pre-initialization ({@code FMLPreInitializationEvent}).
     * {@link #soundManager()} reads it and fails clearly when it is not set yet.
     */
    public static ISoundManager soundManager;
    /**
     * Set during GTCEu's construction ({@code FMLConstructionEvent}), when the core module is created; materials are
     * registered during its pre-initialization. {@link #materialManager()} reads it and fails clearly when it is not
     * set yet.
     */
    public static IMaterialRegistryManager materialManager;
    /**
     * Set during GTCEu's pre-initialization ({@code FMLPreInitializationEvent}), before materials are registered.
     * {@link #markerMaterialRegistry()} reads it and fails clearly when it is not set yet.
     */
    public static MarkerMaterialRegistry markerMaterialRegistry;
    /**
     * Set during GTCEu's pre-initialization ({@code FMLPreInitializationEvent}), after materials are frozen and
     * before the MTE registry event. {@link #mteManager()} reads it and fails clearly when it is not set yet.
     */
    public static MTEManager mteManager;
    /**
     * Set during GTCEu's pre-initialization ({@code FMLPreInitializationEvent}), together with {@link #mteManager}.
     * {@link #coilManager()} reads it and fails clearly when it is not set yet.
     */
    public static CoilManager coilManager;
    /** GT's data migrations API */
    public static final MigrationAPI MIGRATIONS = new MigrationAPI();
    public static final RecipePropertyRegistry RECIPE_PROPERTIES = new RecipePropertyRegistry();
    /**
     * Manager for Item Recycling Data
     */
    public static final RecyclingManager RECYCLING_MANAGER = new RecyclingManager();

    private static final String CONSTRUCTION = "during its construction (FMLConstructionEvent)";
    private static final String PRE_INIT = "during its pre-initialization (FMLPreInitializationEvent)";
    private static final String SERVER_STARTING = "when a server starts (FMLServerStartingEvent)";

    /** Decided at the start of GTCEu's pre-initialization; see {@link #isHighTier()}. */
    private static boolean highTier;
    private static boolean highTierInitialized;
    private static boolean highTierReadEarly;

    public static final GTControlledRegistry<ResourceLocation, CoverDefinition> COVER_REGISTRY = new GTControlledRegistry<>(
            Integer.MAX_VALUE);

    public static final Map<Material, Map<StoneType, IBlockOre>> oreBlockTable = new HashMap<>();
    public static final Object2ObjectMap<IBlockState, IHeatingCoilBlockStats> HEATING_COILS = new Object2ObjectOpenHashMap<>();
    public static final Object2ObjectMap<IBlockState, IBatteryData> PSS_BATTERIES = new Object2ObjectOpenHashMap<>();
    public static final Object2ObjectMap<IBlockState, ICleanroomFilter> CLEANROOM_FILTERS = new Object2ObjectOpenHashMap<>();

    /**
     * @return the module manager
     * @throws IllegalStateException before GTCEu's construction ({@code FMLConstructionEvent}) sets it
     */
    public static @NotNull IModuleManager moduleManager() {
        return requireSet(moduleManager, "moduleManager", CONSTRUCTION);
    }

    /**
     * @return the network handler
     * @throws IllegalStateException before GTCEu's construction ({@code FMLConstructionEvent}) sets it
     */
    public static @NotNull INetworkHandler networkHandler() {
        return requireSet(networkHandler, "networkHandler", CONSTRUCTION);
    }

    /**
     * @return the command manager
     * @throws IllegalStateException until the first server start ({@code FMLServerStartingEvent}) sets it
     */
    public static @NotNull ICommandManager commandManager() {
        return requireSet(commandManager, "commandManager", SERVER_STARTING);
    }

    /**
     * @return the advancement manager
     * @throws IllegalStateException before GTCEu's pre-initialization ({@code FMLPreInitializationEvent}) sets it
     */
    public static @NotNull IAdvancementManager advancementManager() {
        return requireSet(advancementManager, "advancementManager", PRE_INIT);
    }

    /**
     * @return the sound manager
     * @throws IllegalStateException before GTCEu's pre-initialization ({@code FMLPreInitializationEvent}) sets it
     */
    public static @NotNull ISoundManager soundManager() {
        return requireSet(soundManager, "soundManager", PRE_INIT);
    }

    /**
     * @return the material registry manager
     * @throws IllegalStateException before GTCEu's construction ({@code FMLConstructionEvent}) sets it
     */
    public static @NotNull IMaterialRegistryManager materialManager() {
        return requireSet(materialManager, "materialManager", CONSTRUCTION);
    }

    /**
     * @return the marker material registry
     * @throws IllegalStateException before GTCEu's pre-initialization ({@code FMLPreInitializationEvent}) sets it
     */
    public static @NotNull MarkerMaterialRegistry markerMaterialRegistry() {
        return requireSet(markerMaterialRegistry, "markerMaterialRegistry", PRE_INIT);
    }

    /**
     * @return the MetaTileEntity registry manager
     * @throws IllegalStateException before GTCEu's pre-initialization ({@code FMLPreInitializationEvent}) sets it
     */
    public static @NotNull MTEManager mteManager() {
        return requireSet(mteManager, "mteManager", PRE_INIT);
    }

    /**
     * @return the heating coil registry manager
     * @throws IllegalStateException before GTCEu's pre-initialization ({@code FMLPreInitializationEvent}) sets it
     */
    public static @NotNull CoilManager coilManager() {
        return requireSet(coilManager, "coilManager", PRE_INIT);
    }

    private static <T> @NotNull T requireSet(@Nullable T value, @NotNull String name, @NotNull String stage) {
        if (value == null) {
            throw new IllegalStateException("GregTechAPI." + name + " is not available yet: GTCEu sets it " + stage);
        }
        return value;
    }

    /**
     * Whether content above UV is registered: the {@code highTierContent} config or any {@link HighTierEvent}
     * listener enables it.
     * <p>
     * Decided at the start of GTCEu's pre-initialization ({@code FMLPreInitializationEvent}), before GTCEu registers
     * its materials, blocks, items and machines. Inside a {@link HighTierEvent} listener, ask the event instead.
     *
     * Read before it is decided, it answers {@code false}, as it always has, and logs a warning with the caller's
     * stack once, since the answer may change when GTCEu decides it.
     */
    public static boolean isHighTier() {
        if (!highTierInitialized) {
            if (!highTierReadEarly) {
                highTierReadEarly = true;
                GTLog.logger.warn("GregTechAPI.isHighTier() was read before GTCEu decides it at the start of its " +
                        "pre-initialization (FMLPreInitializationEvent); it answers false until then",
                        new IllegalStateException("isHighTier() read early"));
            }
            return false;
        }
        return highTier;
    }

    /**
     * Initializes High-Tier. Internal use only, do not attempt to call this.
     */
    static void initializeHighTier() {
        if (highTierInitialized) throw new IllegalStateException("High-Tier is already initialized.");
        HighTierEvent highTierEvent = new HighTierEvent();
        MinecraftForge.EVENT_BUS.post(highTierEvent);

        highTier = GTConfig.get().highTierContent() || highTierEvent.isHighTier();
        highTierInitialized = true;

        if (GregTechAPI.isHighTier()) GTLog.logger.info("High-Tier is Enabled.");
        else GTLog.logger.info("High-Tier is Disabled.");
    }

    public static class RegisterEvent<V> extends GenericEvent<V> {

        private final GTControlledRegistry<ResourceLocation, V> registry;

        public RegisterEvent(GTControlledRegistry<ResourceLocation, V> registry, Class<V> clazz) {
            super(clazz);
            this.registry = registry;
        }

        public void register(int id, ResourceLocation key, V value) {
            if (registry != null) registry.register(id, key, value);
        }

        public void register(int id, String key, V value) {
            if (registry != null) registry.register(id,
                    new ResourceLocation(Loader.instance().activeModContainer().getModId(), key), value);
        }
    }
}

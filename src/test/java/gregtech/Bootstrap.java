package gregtech;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.GregTechAPIInternal;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.util.GTLog;
import gregtech.api.worldgen.generator.GTWorldGenCapability;
import gregtech.common.CommonProxy;
import gregtech.core.ContentRegistration;
import gregtech.core.CoreModule;
import gregtech.core.unification.material.internal.MaterialRegistryManager;
import gregtech.modules.ModuleManager;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.text.translation.LanguageMap;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.util.CompoundDataFixer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.StartupQuery;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.registries.GameData;

import com.cleanroommc.common.CleanroomEnvironment;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class Bootstrap {

    /** The classes whose {@link CapabilityInject} fields the game's loader would fill. */
    private static final List<Class<?>> CAPABILITY_HOLDERS = List.of(GregtechCapabilities.class,
            GregtechTileCapabilities.class, GTWorldGenCapability.class, CapabilityItemHandler.class,
            CapabilityFluidHandler.class, CapabilityEnergy.class);

    private static boolean bootstrapped = false;
    private static @Nullable Throwable failure;

    private Bootstrap() {}

    /**
     * Prepares this JVM once as the game's loading would: Minecraft's own bootstrap, the capabilities, GregTech's
     * content registered by the routine the core module runs, and GregTech's block and item registry events. A failed
     * bootstrap is reported again to every later caller rather than retried on half-set state.
     */
    public static void perform() {
        if (bootstrapped) {
            return;
        }
        if (failure != null) {
            throw new IllegalStateException("The test bootstrap failed earlier in this JVM", failure);
        }
        try {
            bootstrap();
            bootstrapped = true;
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        }
    }

    private static void bootstrap() {
        CleanroomEnvironment.setSide(Side.SERVER);
        try {
            Field deobfuscatedEnvironment = CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");
            deobfuscatedEnvironment.setAccessible(true);
            deobfuscatedEnvironment.setBoolean(null, true);

            Method setLocale = I18n.class.getDeclaredMethod("setLocale", Locale.class); // No need to care about
                                                                                        // obfuscation
            setLocale.setAccessible(true);
            setLocale.invoke(null, new Locale());

            // set FMLCommonHandler#sidedDelegate, since MaterialIconType and LocalizationUtils uses it
            Field sidedDelegate = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
            sidedDelegate.setAccessible(true);
            sidedDelegate.set(FMLCommonHandler.instance(), new TestSidedHandler());
        } catch (NoSuchFieldException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("Unexpected exception on test bootstrap", e);
        }
        net.minecraft.init.Bootstrap.register();
        ModMetadata meta = new ModMetadata();
        meta.modId = GTValues.MODID;
        Loader.instance().setupTestHarness(new DummyModContainer(meta));
        GregTechAPI.moduleManager = ModuleManager.getInstance();
        // the server-side proxy the loader injects on a dedicated server
        CoreModule.proxy = new CommonProxy();
        // as the core module's constructor does
        GregTechAPI.materialManager = MaterialRegistryManager.getInstance();
        injectCapabilities();

        // the content registration of the core module's pre-initialization, after the high-tier switch it reads
        GregTechAPIInternal.preInit();
        ContentRegistration.register();

        // the block and item registry events, through GregTech's own handlers in the order Forge calls them: ore
        // blocks, item blocks and meta item values exist only after these. The recipe event is left out; tests
        // build the recipes they need.
        RegistryEvent.Register<Block> blocks = new RegistryEvent.Register<>(GameData.BLOCKS, ForgeRegistries.BLOCKS);
        CommonProxy.registerBlocks(blocks);
        CommonProxy.registerBlocksLast(blocks);
        CommonProxy.registerItems(new RegistryEvent.Register<>(GameData.ITEMS, ForgeRegistries.ITEMS));
    }

    /**
     * Stands in for the loader's scan of {@link CapabilityInject} fields, which a unit-test JVM does not run, so the
     * capability registration that follows sets them as in the game; then registers the capabilities Forge registers
     * in its own pre-initialization. GregTech's own are registered by {@link ContentRegistration}.
     */
    private static void injectCapabilities() {
        ASMDataTable table = new ASMDataTable();
        for (Class<?> holder : CAPABILITY_HOLDERS) {
            for (Field field : holder.getDeclaredFields()) {
                CapabilityInject inject = field.getAnnotation(CapabilityInject.class);
                if (inject != null) {
                    table.addASMData(null, CapabilityInject.class.getName(), holder.getName(), field.getName(),
                            Collections.singletonMap("value", Type.getType(inject.value())));
                }
            }
        }
        CapabilityManager.INSTANCE.injectCapabilities(table);
        CapabilityItemHandler.register();
        CapabilityFluidHandler.register();
        CapabilityEnergy.register();
    }

    private static void loadGregtechLangFile() {
        try (InputStream is = LanguageMap.class.getResourceAsStream("/assets/gregtech/lang/en_us.lang")) {
            if (is != null) {
                LanguageMap.inject(is);
            } else {
                GTLog.logger.error("Couldn't find en_us.lang file");
            }
        } catch (IOException ex) {
            GTLog.logger.error("Unable to read en_us.lang file", ex);
        }
    }

    private static final class TestSidedHandler implements IFMLSidedHandler {

        @Override
        public List<String> getAdditionalBrandingInformation() {
            return Collections.emptyList();
        }

        @Override
        public Side getSide() {
            return Side.SERVER;
        }

        @Override
        public void haltGame(String message, Throwable exception) {
            throw new RuntimeException(message, exception);
        }

        @Override
        public void showGuiScreen(Object clientGuiElement) {}

        @Override
        public void queryUser(StartupQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void beginServerLoading(MinecraftServer server) {}

        @Override
        public void finishServerLoading() {}

        @Override
        public File getSavesDirectory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MinecraftServer getServer() {
            return null; // Unit fixtures have no live Minecraft server.
        }

        @Override
        public boolean isDisplayCloseRequested() {
            return false;
        }

        @Override
        public boolean shouldServerShouldBeKilledQuietly() {
            return false;
        }

        @Override
        public void addModAsResource(ModContainer container) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCurrentLanguage() {
            return "en_US";
        }

        @Override
        public void serverStopped() {}

        @Override
        public NetworkManager getClientToServerNetworkManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public INetHandler getClientPlayHandler() {
            return null;
        }

        @Override
        public void fireNetRegistrationEvent(EventBus bus, NetworkManager manager, Set<String> channelSet,
                                             String channel, Side side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean shouldAllowPlayerLogins() {
            return false;
        }

        @Override
        public void allowLogins() {}

        @Override
        public IThreadListener getWorldThread(INetHandler net) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void processWindowMessages() {}

        @Override
        public String stripSpecialChars(String message) {
            return message;
        }

        @Override
        public void reloadRenderers() {}

        @Override
        public void fireSidedRegistryEvents() {}

        @Override
        public CompoundDataFixer getDataFixer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDisplayVSyncForced() {
            return false;
        }
    }
}

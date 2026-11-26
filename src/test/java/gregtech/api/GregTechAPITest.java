package gregtech.api;

import gregtech.Bootstrap;
import gregtech.api.advancement.IAdvancementManager;
import gregtech.api.block.coil.CoilManager;
import gregtech.api.command.ICommandManager;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.modules.IModuleManager;
import gregtech.api.network.INetworkHandler;
import gregtech.api.sound.ISoundManager;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.api.unification.material.registry.MarkerMaterialRegistry;
import gregtech.common.ConfigHolder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GregTechAPITest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    static Stream<Accessor<?>> accessors() throws ReflectiveOperationException {
        String construction = "FMLConstructionEvent", preInit = "FMLPreInitializationEvent";
        return Stream.of(
                new Accessor<>("moduleManager", construction, stub(IModuleManager.class),
                        () -> GregTechAPI.moduleManager, v -> GregTechAPI.moduleManager = v,
                        GregTechAPI::moduleManager),
                new Accessor<>("networkHandler", construction, stub(INetworkHandler.class),
                        () -> GregTechAPI.networkHandler, v -> GregTechAPI.networkHandler = v,
                        GregTechAPI::networkHandler),
                new Accessor<>("commandManager", "FMLServerStartingEvent", stub(ICommandManager.class),
                        () -> GregTechAPI.commandManager, v -> GregTechAPI.commandManager = v,
                        GregTechAPI::commandManager),
                new Accessor<>("advancementManager", preInit, stub(IAdvancementManager.class),
                        () -> GregTechAPI.advancementManager, v -> GregTechAPI.advancementManager = v,
                        GregTechAPI::advancementManager),
                new Accessor<>("soundManager", preInit, stub(ISoundManager.class),
                        () -> GregTechAPI.soundManager, v -> GregTechAPI.soundManager = v,
                        GregTechAPI::soundManager),
                new Accessor<>("materialManager", construction, stub(IMaterialRegistryManager.class),
                        () -> GregTechAPI.materialManager, v -> GregTechAPI.materialManager = v,
                        GregTechAPI::materialManager),
                new Accessor<>("markerMaterialRegistry", preInit, construct(MarkerMaterialRegistry.class),
                        () -> GregTechAPI.markerMaterialRegistry, v -> GregTechAPI.markerMaterialRegistry = v,
                        GregTechAPI::markerMaterialRegistry),
                new Accessor<>("mteManager", preInit, construct(MTEManager.class),
                        () -> GregTechAPI.mteManager, v -> GregTechAPI.mteManager = v,
                        GregTechAPI::mteManager),
                new Accessor<>("coilManager", preInit, construct(CoilManager.class),
                        () -> GregTechAPI.coilManager, v -> GregTechAPI.coilManager = v,
                        GregTechAPI::coilManager));
    }

    @ParameterizedTest
    @MethodSource("accessors")
    void accessorNamesTheStageUntilTheFieldIsSetAndThenReturnsIt(Accessor<?> accessor) {
        accessor.check();
    }

    @Test
    void isHighTierThrowsUntilPreInitializationDecidesIt() throws ReflectiveOperationException {
        Field decided = GregTechAPI.class.getDeclaredField("highTierInitialized");
        decided.setAccessible(true);
        // the test bootstrap decides it the way the core module's pre-initialization does
        assertTrue(decided.getBoolean(null));
        boolean previous = GregTechAPI.isHighTier();
        try {
            decided.setBoolean(null, false);
            IllegalStateException e = assertThrows(IllegalStateException.class, GregTechAPI::isHighTier);
            assertTrue(e.getMessage().contains("FMLPreInitializationEvent"), e.getMessage());

            GregTechAPI.initializeHighTier();
            // no high-tier event listener is registered here, so only the config decides
            assertEquals(ConfigHolder.machines.highTierContent, GregTechAPI.isHighTier());
            assertThrows(IllegalStateException.class, GregTechAPI::initializeHighTier);
        } finally {
            decided.setBoolean(null, true);
            Field highTier = GregTechAPI.class.getDeclaredField("highTier");
            highTier.setAccessible(true);
            highTier.setBoolean(null, previous);
        }
    }

    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> "toString".equals(method.getName()) ? "stub " + type.getSimpleName() : null));
    }

    private static <T> T construct(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private record Accessor<T>(String field, String stage, T value, Supplier<T> read, Consumer<T> write,
                               Supplier<T> accessor) {

        void check() {
            T previous = read.get();
            try {
                write.accept(null);
                IllegalStateException e = assertThrows(IllegalStateException.class, accessor::get);
                assertTrue(e.getMessage().contains("GregTechAPI." + field), e.getMessage());
                assertTrue(e.getMessage().contains(stage), e.getMessage());

                write.accept(value);
                assertSame(value, accessor.get());
            } finally {
                write.accept(previous);
            }
        }

        @Override
        public String toString() {
            return field;
        }
    }
}

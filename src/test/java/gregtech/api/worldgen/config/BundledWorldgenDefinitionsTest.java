package gregtech.api.worldgen.config;

import gregtech.BootstrapExtension;
import gregtech.api.util.FileUtility;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.api.worldgen.filler.FillerEntry;
import gregtech.api.worldgen.populator.IVeinPopulator;
import gregtech.api.worldgen.populator.SurfaceRockPopulator;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads the worldgen definitions bundled under {@code assets/gregtech/worldgen} through the real loader, as the core
 * module's initialization does into a fresh config folder, and checks that every file became a definition whose
 * materials, blocks and fluids resolve.
 */
@ExtendWith(BootstrapExtension.class)
class BundledWorldgenDefinitionsTest {

    @TempDir
    static Path configRoot;

    private static File previousConfigDir;
    private static Map<BedrockFluidDepositDefinition, Integer> previousFluidVeins;

    @BeforeAll
    static void load() throws ReflectiveOperationException {
        Field configDir = Loader.class.getDeclaredField("canonicalConfigDir");
        configDir.setAccessible(true);
        previousConfigDir = (File) configDir.get(Loader.instance());
        configDir.set(Loader.instance(), configRoot.toFile());
        previousFluidVeins = new LinkedHashMap<>(BedrockFluidVeinHandler.veinList);

        WorldGenRegistry.INSTANCE.initializeRegistry();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        Field configDir = Loader.class.getDeclaredField("canonicalConfigDir");
        configDir.setAccessible(true);
        configDir.set(Loader.instance(), previousConfigDir);
        BedrockFluidVeinHandler.veinList.clear();
        BedrockFluidVeinHandler.veinList.putAll(previousFluidVeins);
    }

    static Stream<String> bundledFiles() throws IOException, URISyntaxException {
        Path root = bundledRoot();
        try (Stream<Path> files = Files.walk(root)) {
            List<String> names = files.filter(Files::isRegularFile)
                    .map(file -> FileUtility.nativeSepToSlash(root.relativize(file).toString()))
                    .sorted().toList();
            assertFalse(names.isEmpty(), "no bundled worldgen files found");
            return names.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bundledFiles")
    void bundledDefinitionLoadsAndResolves(String file) throws IOException, URISyntaxException {
        JsonObject json = FileUtility.tryExtractFromFile(bundledRoot().resolve(file));
        assertNotNull(json, () -> file + " is not a JSON object");
        if (file.startsWith("vein/")) {
            assertOreVein(file.substring("vein/".length()), json);
        } else if (file.startsWith("fluid/")) {
            assertFluidVein(file.substring("fluid/".length()));
        } else if (file.equals("dimensions.json")) {
            assertDimensionNames(json);
        } else if (file.equals("worldgen_extracted.json")) {
            assertExtractionVersions(json);
        } else {
            fail(file + " is a worldgen file the loader does not read");
        }
    }

    @Test
    void theLoaderReadsEveryBundledVein() throws IOException, URISyntaxException {
        long veins = bundledFiles().filter(file -> file.startsWith("vein/")).count();
        long fluids = bundledFiles().filter(file -> file.startsWith("fluid/")).count();
        assertEquals(veins, WorldGenRegistry.getOreDeposits().size());
        assertEquals(fluids, WorldGenRegistry.getBedrockVeinDeposits().size());
    }

    private static void assertOreVein(String name, JsonObject json) {
        OreDepositDefinition loaded = WorldGenRegistry.getOreDeposits().stream()
                .filter(definition -> definition.getDepositName().equals(name)).findFirst().orElse(null);
        if (loaded == null) {
            // the loader logs and skips a definition it cannot read: read it again for the reason
            new OreDepositDefinition(name).initializeFromConfig(json);
            fail("the loader skipped vein " + name + ", though it reads on its own");
        }
        assertNotNull(loaded.getShapeGenerator(), name);
        List<FillerEntry> entries = loaded.getBlockFiller().getAllPossibleStates();
        assertFalse(entries.isEmpty(), () -> name + " fills no blocks");
        for (FillerEntry entry : entries) {
            assertFalse(entry.getPossibleResults().isEmpty(), () -> name + " has a filler entry with no block");
            for (IBlockState state : entry.getPossibleResults()) {
                assertNotNull(state, name);
                assertNotSame(Blocks.AIR, state.getBlock(), () -> name + " resolves a block to air");
            }
        }
        IVeinPopulator populator = loaded.getVeinPopulator();
        if (populator instanceof SurfaceRockPopulator surfaceRock) {
            assertNotNull(surfaceRock.getMaterial(), name);
            assertNotNull(MetaBlocks.SURFACE_ROCK.get(surfaceRock.getMaterial()),
                    () -> name + " places surface rocks of a material that has none");
        }
    }

    private static void assertFluidVein(String name) {
        BedrockFluidDepositDefinition loaded = WorldGenRegistry.getBedrockVeinDeposits().stream()
                .filter(definition -> definition.getDepositName().equals(name)).findFirst().orElse(null);
        assertNotNull(loaded, () -> "the loader skipped fluid vein " + name + "; its log says why");
        assertNotNull(loaded.getStoredFluid(), name);
        assertTrue(BedrockFluidVeinHandler.veinList.containsKey(loaded), () -> name + " is not in the vein pool");
    }

    private static void assertDimensionNames(JsonObject json) {
        for (JsonElement dimension : json.getAsJsonArray("dims")) {
            JsonObject entry = dimension.getAsJsonObject();
            assertEquals(entry.get("dimName").getAsString(),
                    WorldGenRegistry.getNamedDimensions().get(entry.get("dimID").getAsInt()));
        }
    }

    /** The lock file must name the versions the loader expects, or every start extracts the defaults again. */
    private static void assertExtractionVersions(JsonObject json) {
        assertEquals(version("FLUID_VEIN_VERSION"), json.get("fluidVersion").getAsInt());
        assertEquals(version("ORE_VEIN_VERSION"), json.get("veinVersion").getAsInt());
    }

    private static int version(String name) {
        try {
            Field field = WorldGenRegistry.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Path bundledRoot() throws URISyntaxException {
        URL url = WorldGenRegistry.class.getResource("/assets/gregtech/worldgen");
        assertNotNull(url, "the bundled worldgen folder is not on the test classpath");
        return Paths.get(url.toURI());
    }
}

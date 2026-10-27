package gregtech.common.pipelike.itempipe.net;

import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.world.DummySaveHandler;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ItemPipeMapStorageNormalizationTest {

    private static final String NAME = "gregtech.item_pipe_net";
    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @TempDir
    Path directory;

    @Test
    void resistanceOnlyDiskRecordIsDirtyAndActuallyRewrittenWithLegacyRange() throws IOException {
        NBTTagCompound original = canonicalRecord(64);
        properties(original).removeTag("Range");
        DiskFiles files = writeDiskRecord(original);
        MapStorage storage = new MapStorage(files);
        WorldItemPipeNet loaded = load(storage);

        assertEquals(64, priority(loaded));
        assertTrue(loaded.isDirty(), "An otherwise untouched graph must reach MapStorage's save path");
        storage.saveAllData();

        assertFalse(loaded.isDirty());
        assertCanonicalDisk(files, 64);
        WorldItemPipeNet reloaded = load(new MapStorage(files));
        assertEquals(64, priority(reloaded));
        assertFalse(reloaded.isDirty(), "The normalized graph must not require another rewrite");
    }

    @Test
    void canonicalDiskRecordStaysCleanAndIsNotRewritten() throws IOException {
        DiskFiles files = writeDiskRecord(canonicalRecord(64));
        byte[] originalBytes = Files.readAllBytes(files.getMapFileFromName(NAME).toPath());
        MapStorage storage = new MapStorage(files);
        WorldItemPipeNet loaded = load(storage);

        assertEquals(64, priority(loaded));
        assertFalse(loaded.isDirty());
        storage.saveAllData();

        assertArrayEquals(originalBytes, Files.readAllBytes(files.getMapFileFromName(NAME).toPath()));
    }

    @Test
    void rangeOnlyConflictingAndMixedTypedAliasesNormalizeTheExistingDecoderChoice() throws IOException {
        NBTTagCompound rangeOnly = canonicalRecord(19);
        properties(rangeOnly).removeTag("Resistance");
        NBTTagCompound conflicting = canonicalRecord(64);
        properties(conflicting).setInteger("Range", 17);
        NBTTagCompound invalidResistance = canonicalRecord(27);
        properties(invalidResistance).setString("Resistance", "malformed");
        NBTTagCompound invalidRange = canonicalRecord(31);
        properties(invalidRange).setString("Range", "malformed");
        NBTTagCompound[] records = { rangeOnly, conflicting, invalidResistance, invalidRange };
        int[] expected = { 19, 64, 27, 31 };
        for (int index = 0; index < records.length; index++) {
            DiskFiles files = writeDiskRecord(records[index]);
            MapStorage storage = new MapStorage(files);
            WorldItemPipeNet loaded = load(storage);

            assertEquals(expected[index], priority(loaded));
            assertTrue(loaded.isDirty(), "Noncanonical fixture " + index + " must be rewritten");
            storage.saveAllData();
            assertCanonicalDisk(files, expected[index]);
            assertFalse(load(new MapStorage(files)).isDirty());
        }
    }

    @Test
    void rejectedManualReloadPreservesGoodGraphWriterAndDirtyState() throws IOException {
        for (boolean initiallyDirty : new boolean[] { false, true }) {
            NBTTagCompound original = canonicalRecord(64);
            if (initiallyDirty) properties(original).removeTag("Range");
            MapStorage storage = new MapStorage(writeDiskRecord(original));
            WorldItemPipeNet loaded = load(storage);
            ItemPipeNet oldNet = loaded.getNetFromPos(POS);
            NBTTagCompound goodWriter = loaded.writeToNBT(new NBTTagCompound());
            assertEquals(initiallyDirty, loaded.isDirty());

            assertThrows(IllegalArgumentException.class, () -> loaded.readFromNBT(malformedRecord()));

            assertSame(oldNet, loaded.getNetFromPos(POS));
            assertEquals(64, priority(loaded));
            assertFalse(loaded.hasQuarantinedData());
            assertEquals(initiallyDirty, loaded.isDirty(), "Temporary decoder flags cannot dirty a failed read");
            assertEquals(goodWriter, loaded.writeToNBT(new NBTTagCompound()));
        }
    }

    @Test
    void rejectedInitialReadDoesNotNormalizeOrReplaceRawQuarantinedDiskData() throws IOException {
        NBTTagCompound malformed = malformedRecord();
        DiskFiles files = writeDiskRecord(malformed);
        MapStorage storage = new MapStorage(files);
        WorldItemPipeNet loaded = load(storage); // MapStorage catches the production reader exception.

        assertTrue(loaded.hasQuarantinedData());
        assertFalse(loaded.isDirty());
        assertNull(loaded.getNetFromPos(POS));
        assertEquals(malformed, loaded.writeToNBT(new NBTTagCompound()));
        loaded.markDirty();
        storage.saveAllData();

        assertEquals(malformed, readDiskRecord(files));
    }

    private DiskFiles writeDiskRecord(NBTTagCompound data) throws IOException {
        DiskFiles files = new DiskFiles(this.directory);
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("data", data);
        try (FileOutputStream out = new FileOutputStream(files.getMapFileFromName(NAME))) {
            CompressedStreamTools.writeCompressed(wrapper, out);
        }
        return files;
    }

    private static WorldItemPipeNet load(MapStorage storage) {
        WorldItemPipeNet result = (WorldItemPipeNet) storage.getOrLoadData(WorldItemPipeNet.class, NAME);
        assertNotNull(result);
        return result;
    }

    private static int priority(WorldItemPipeNet world) {
        ItemPipeNet net = world.getNetFromPos(POS);
        assertNotNull(net);
        return net.getNodeAt(POS).data.getPriority();
    }

    private static void assertCanonicalDisk(DiskFiles files, int priority) throws IOException {
        NBTTagCompound savedProperties = properties(readDiskRecord(files));
        assertTrue(savedProperties.hasKey("Resistance", 3));
        assertTrue(savedProperties.hasKey("Range", 3));
        assertEquals(priority, savedProperties.getInteger("Resistance"));
        assertEquals(priority, savedProperties.getInteger("Range"));
    }

    private static NBTTagCompound readDiskRecord(DiskFiles files) throws IOException {
        try (FileInputStream in = new FileInputStream(files.getMapFileFromName(NAME))) {
            return CompressedStreamTools.readCompressed(in).getCompoundTag("data");
        }
    }

    private static NBTTagCompound canonicalRecord(int priority) {
        WorldItemPipeNet world = new WorldItemPipeNet(NAME);
        world.addNode(POS, new ItemPipeProperties(priority, 0.5f), 0, 63, false);
        return world.writeToNBT(new NBTTagCompound());
    }

    private static NBTTagCompound properties(NBTTagCompound record) {
        return record.getTagList("PipeNets", 10).getCompoundTagAt(0).getCompoundTag("Nodes")
                .getTagList("WireProperties", 10).getCompoundTagAt(0);
    }

    private static NBTTagCompound malformedRecord() {
        NBTTagCompound malformed = canonicalRecord(99);
        properties(malformed).removeTag("Range"); // Flag a rewrite before a later node fails validation.
        malformed.setString("unknownPayload", "must survive quarantine unchanged");
        malformed.getTagList("PipeNets", 10).getCompoundTagAt(0).getCompoundTag("Nodes")
                .getTagList("NodeIndexes", 10).getCompoundTagAt(0).setInteger("open", 64);
        return malformed;
    }

    private static final class DiskFiles extends DummySaveHandler {

        private final Path dataDirectory;

        private DiskFiles(Path directory) throws IOException {
            this.dataDirectory = Files.createDirectories(directory.resolve("data"));
        }

        @Override
        public File getMapFileFromName(String name) {
            return this.dataDirectory.resolve(name + ".dat").toFile();
        }
    }
}

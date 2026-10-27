package gregtech.api.pipenet;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetQuarantineTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final String NAME = "pipe-quarantine";

    @TempDir
    Path directory;

    @Test
    void actualMapStorageCaughtDecodeFailureCannotOverwriteTheRejectedRecord() throws IOException {
        QuarantineDiskSaveHandler files = new QuarantineDiskSaveHandler(this.directory);
        NBTTagCompound bad = malformedRecord();
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("data", bad);
        try (FileOutputStream out = new FileOutputStream(files.getMapFileFromName(NAME))) {
            CompressedStreamTools.writeCompressed(wrapper, out);
        }
        MapStorage storage = new MapStorage(files);

        QuarantineStorageWorld loaded = (QuarantineStorageWorld) storage.getOrLoadData(QuarantineStorageWorld.class,
                NAME);

        assertNotNull(loaded, "MapStorage catches the decoder exception and returns its created object");
        assertTrue(loaded.hasQuarantinedData());
        assertNull(loaded.getNetFromPos(POS));
        loaded.addNode(POS, 5, 0, 63, true);
        storage.saveAllData();
        NBTTagCompound written;
        try (FileInputStream in = new FileInputStream(files.getMapFileFromName(NAME))) {
            written = CompressedStreamTools.readCompressed(in).getCompoundTag("data");
        }
        assertEquals(bad, written, "Runtime reconstruction must not replace the rejected saved record");
    }

    @Test
    void failedManualReloadKeepsAnExistingValidGraphWriter() {
        QuarantineStorageWorld world = new QuarantineStorageWorld(NAME);
        world.addNode(POS, 7, 0, 63, false);
        NBTTagCompound good = world.writeToNBT(new NBTTagCompound());

        assertThrows(IllegalArgumentException.class, () -> world.readFromNBT(malformedRecord()));

        assertFalse(world.hasQuarantinedData());
        assertEquals(good, world.writeToNBT(new NBTTagCompound()));
    }

    @Test
    void acceptedEmptySaveIsNotReplacedByALaterMalformedManualInput() {
        QuarantineStorageWorld world = new QuarantineStorageWorld(NAME);
        world.readFromNBT(new NBTTagCompound());
        NBTTagCompound good = world.writeToNBT(new NBTTagCompound());

        assertThrows(IllegalArgumentException.class, () -> world.readFromNBT(malformedRecord()));

        assertFalse(world.hasQuarantinedData());
        assertEquals(good, world.writeToNBT(new NBTTagCompound()));
    }

    @Test
    void quarantineCopiesInputAndOutputAndRequiresAValidatedReplacementToClear() {
        QuarantineStorageWorld world = new QuarantineStorageWorld(NAME);
        NBTTagCompound bad = malformedRecord();
        NBTTagCompound original = bad.copy();
        assertThrows(IllegalArgumentException.class, () -> world.readFromNBT(bad));
        bad.setString("unknownPayload", "changed input");
        NBTTagCompound firstWrite = world.writeToNBT(new NBTTagCompound());
        firstWrite.setString("unknownPayload", "changed output");
        assertEquals(original, world.writeToNBT(new NBTTagCompound()));

        QuarantineStorageWorld repair = new QuarantineStorageWorld(NAME);
        repair.addNode(POS, 9, 0, 0, false);
        world.readFromNBT(repair.writeToNBT(new NBTTagCompound()));

        assertFalse(world.hasQuarantinedData());
        assertEquals(Integer.valueOf(9), world.getNetFromPos(POS).getNodeAt(POS).data);
    }

    private static NBTTagCompound malformedRecord() {
        QuarantineStorageWorld original = new QuarantineStorageWorld(NAME);
        original.addNode(POS, 1, 0, 63, true);
        NBTTagCompound malformed = original.writeToNBT(new NBTTagCompound());
        malformed.setString("unknownPayload", "must survive without silent repair");
        malformed.getTagList("PipeNets", 10).getCompoundTagAt(0).getCompoundTag("Nodes")
                .getTagList("NodeIndexes", 10).getCompoundTagAt(0).setInteger("open", 64);
        return malformed;
    }
}

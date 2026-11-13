package gregtech.api.util.virtualregistry;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualEnderMigrationTest {

    @Test
    void theLegacyFileIsMovedAsideOnce(@TempDir File dir) throws IOException {
        File legacy = new File(dir, "gregtech.vtank_data.dat");
        writeLegacy(legacy);

        VirtualEnderRegistry.migrateLegacyFile(new VirtualEnderRegistry("test"), legacy);

        assertFalse(legacy.exists());
        assertTrue(new File(dir, "gregtech.vtank_data.backup.dat").isFile());
    }

    @Test
    void anEarlierBackupIsKept(@TempDir File dir) throws IOException {
        File legacy = new File(dir, "gregtech.vtank_data.dat");
        writeLegacy(new File(dir, "gregtech.vtank_data.backup.dat"));
        writeLegacy(legacy);

        File backup = VirtualEnderRegistry.backupFileFor(legacy);

        assertEquals("gregtech.vtank_data.backup1.dat", backup.getName());
    }

    private static void writeLegacy(File file) throws IOException {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("data", new NBTTagCompound());
        try (FileOutputStream out = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }
}

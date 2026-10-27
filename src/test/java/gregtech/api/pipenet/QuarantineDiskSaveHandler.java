package gregtech.api.pipenet;

import gregtech.api.util.world.DummySaveHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class QuarantineDiskSaveHandler extends DummySaveHandler {

    private final Path dataDirectory;

    QuarantineDiskSaveHandler(Path directory) throws IOException {
        this.dataDirectory = Files.createDirectories(directory.resolve("data"));
    }

    @Override
    public File getMapFileFromName(String name) {
        return this.dataDirectory.resolve(name + ".dat").toFile();
    }
}

package gregtech.api.configuration;

import gregtech.api.util.world.DummySaveHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class PipeDimensionSaveHandler extends DummySaveHandler {

    private final Path directory;

    PipeDimensionSaveHandler(Path directory) throws IOException {
        this.directory = Files.createDirectories(directory.resolve("data"));
    }

    @Override
    public File getMapFileFromName(String name) {
        return this.directory.resolve(name + ".dat").toFile();
    }
}

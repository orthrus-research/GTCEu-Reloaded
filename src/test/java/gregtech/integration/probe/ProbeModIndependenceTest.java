package gregtech.integration.probe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The One Probe and HWYLA are optional: only their integration packages, and the One Probe mixin that applies only
 * with it, may name their classes. The shared probe model in particular must load without either.
 */
public class ProbeModIndependenceTest {

    private static final List<String> ALLOWED = List.of("gregtech/integration/theoneprobe/",
            "gregtech/integration/hwyla/", "gregtech/mixins/theoneprobe/");

    private static Path findSources() {
        for (Path directory = Paths.get("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path sources = directory.resolve("src/main/java");
            if (Files.isDirectory(sources.resolve("gregtech/integration/probe"))) return sources;
        }
        return null;
    }

    @Test
    public void onlyTheIntegrationsNameProbeModClasses() throws IOException {
        Path sources = findSources();
        assertNotNull(sources, "main sources not found from the working directory");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String relative = sources.relativize(file).toString().replace('\\', '/');
                if (ALLOWED.stream().anyMatch(relative::startsWith)) continue;
                String source = Files.readString(file);
                if (source.contains("mcjty.theoneprobe") || source.contains("mcp.mobius.waila")) {
                    offenders.add(relative);
                }
            }
        }
        assertEquals(List.of(), offenders);
    }
}

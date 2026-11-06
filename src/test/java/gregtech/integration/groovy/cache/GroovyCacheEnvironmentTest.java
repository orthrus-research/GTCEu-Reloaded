package gregtech.integration.groovy.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GroovyCacheEnvironmentTest {

    @TempDir
    Path root;

    @Test
    void localArchiveEntryResolvesToWholeArchiveIncludingEncodedSpaces() throws Exception {
        Path archive = Files.writeString(root.resolve("mod with spaces.jar"), "first bytes");
        var source = URI.create("jar:" + archive.toUri() + "!/package/Mod.class").toURL();
        Path resolved = GroovyCacheEnvironment.localSource(source);
        assertEquals(archive.toAbsolutePath().normalize(), resolved);
        String before = ScriptCacheIdentity.environment(List.of(resolved), "compiler");
        Files.writeString(archive, "changed bytes");
        assertNotEquals(before, ScriptCacheIdentity.environment(List.of(resolved), "compiler"));
    }

    @Test
    void looseClassDirectoryParticipatesInIdentity() throws Exception {
        Path directory = Files.createDirectory(root.resolve("classes"));
        Path resolved = GroovyCacheEnvironment.localSource(directory.toUri().toURL());
        assertEquals(directory, resolved);
        String before = ScriptCacheIdentity.environment(List.of(resolved), "compiler");
        Files.writeString(directory.resolve("Mod.class"), "new class");
        assertNotEquals(before, ScriptCacheIdentity.environment(List.of(resolved), "compiler"));
    }

    @Test
    void malformedTickCentralStyleSourceIsAnUnverifiableDependency() throws Exception {
        var malformed = URI.create("file:/E:/instance/file:/E:/instance/mods/TickCentral-3.2.jar!/Mod.class")
                .toURL();
        if (root.getFileSystem().getSeparator().equals("\\")) {
            assertThrows(IOException.class, () -> GroovyCacheEnvironment.localSource(malformed));
        } else {
            assertFalse(Files.exists(GroovyCacheEnvironment.localSource(malformed)));
        }
        var invalidOnEveryPlatform = URI.create("file:/invalid%00path.jar").toURL();
        assertThrows(IOException.class, () -> GroovyCacheEnvironment.localSource(invalidOnEveryPlatform));
    }

    @Test
    void missingPlaceholderAppearanceInvalidatesAndRemoteSourcesRemainUnverifiable() throws Exception {
        var missing = root.resolve("missing.jar").toUri().toURL();
        Path resolved = GroovyCacheEnvironment.localSource(missing);
        String before = ScriptCacheIdentity.environment(List.of(resolved), "compiler");
        Files.writeString(resolved, "new dependency");
        assertNotEquals(before, ScriptCacheIdentity.environment(List.of(resolved), "compiler"));
        var remote = URI.create("https://example.invalid/dependency.jar").toURL();
        assertThrows(IOException.class, () -> GroovyCacheEnvironment.localSource(remote));
    }

    @Test
    void generatedTransformerBytesAndOrderBothParticipate() throws Exception {
        var content = new AtomicReference<>(new byte[] { 1, 2, 3 });
        URL source = new URL(null, "asmgen:/", new URLStreamHandler() {

            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {

                    @Override
                    public void connect() {}

                    @Override
                    public InputStream getInputStream() throws IOException {
                        assertEquals("/$wrapper/test/Transformer.class", url.getPath());
                        byte[] bytes = content.get();
                        if (bytes == null) throw new IOException("Generated class unavailable");
                        return new ByteArrayInputStream(bytes);
                    }
                };
            }
        });
        List<String> order = List.of("$wrapper.test.Transformer", "test.OtherTransformer");
        String first = GroovyCacheEnvironment.generatedTransformerIdentity(source, order);
        assertEquals(first, GroovyCacheEnvironment.generatedTransformerIdentity(source, order));
        assertNotEquals(first, GroovyCacheEnvironment.generatedTransformerIdentity(source, order.reversed()));
        content.set(new byte[] { 4, 5, 6 });
        assertNotEquals(first, GroovyCacheEnvironment.generatedTransformerIdentity(source, order));
        content.set(null);
        assertThrows(IOException.class, () -> GroovyCacheEnvironment.generatedTransformerIdentity(source, order));
    }
}

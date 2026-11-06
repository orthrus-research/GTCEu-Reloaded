package gregtech.integration.groovy.cache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Content-based cache validation. This class neither compiles scripts nor deletes engine-owned cache files. */
public final class ScriptCacheIdentity {

    private static final String MANIFEST = "_gtceu_content_fingerprint";
    private static final int VERSION = 1;

    private ScriptCacheIdentity() {}

    public record Inputs(String sources, String environment) {}

    public static String sources(Path root) throws IOException {
        return tree(root);
    }

    /** Dependency order is significant; absolute installation paths are not. */
    public static String environment(List<Path> dependencies, String configuration) throws IOException {
        MessageDigest digest = digest();
        text(digest, configuration);
        for (Path path : dependencies) {
            text(digest, path.getFileName().toString());
            // Loader metadata can name absent placeholders (for example minecraft.jar). Such a
            // source supplies no classes, but its later appearance must still invalidate the cache.
            text(digest, Files.notExists(path) ? "missing" : Files.isDirectory(path) ? tree(path) : file(path));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static boolean reusable(Path cache, Inputs expected) {
        try (var input = new DataInputStream(Files.newInputStream(cache.resolve(MANIFEST)))) {
            return input.readInt() == VERSION && input.readUTF().equals(expected.sources()) &&
                    input.readUTF().equals(expected.environment()) && input.readUTF().equals(tree(cache)) &&
                    input.read() == -1;
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }

    public static void invalidate(Path cache) throws IOException {
        Files.deleteIfExists(cache.resolve(MANIFEST));
    }

    public static void seal(Path cache, Inputs inputs) throws IOException {
        Files.createDirectories(cache);
        String compiled = tree(cache);
        Path temporary = Files.createTempFile(cache, MANIFEST, ".tmp");
        try {
            try (var output = new DataOutputStream(Files.newOutputStream(temporary))) {
                output.writeInt(VERSION);
                output.writeUTF(inputs.sources());
                output.writeUTF(inputs.environment());
                output.writeUTF(compiled);
            }
            try {
                Files.move(temporary, cache.resolve(MANIFEST), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, cache.resolve(MANIFEST), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String tree(Path root) throws IOException {
        MessageDigest digest = digest();
        if (!Files.exists(root)) return HexFormat.of().formatHex(digest.digest());
        try (var walk = Files.walk(root, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.equals(MANIFEST)) continue;
                text(digest, relative);
                text(digest, file(path));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String file(Path path) throws IOException {
        MessageDigest digest = digest();
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void text(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}

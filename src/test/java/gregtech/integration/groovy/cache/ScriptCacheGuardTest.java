package gregtech.integration.groovy.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCacheGuardTest {

    private static final Logger LOGGER = LogManager.getLogger("ScriptCacheGuardTest");

    @TempDir
    Path root;

    @Test
    void verifiedCacheIsReusedOnlyAfterASuccessfulSeal() throws IOException {
        Path scripts = scripts();
        Path cache = Files.createDirectories(root.resolve("cache"));
        var first = new ScriptCacheGuard(cache, scripts, () -> true, LOGGER);
        assertFalse(first.open(() -> "environment"));
        first.beforeRun();
        first.afterWrite();
        assertTrue(new ScriptCacheGuard(cache, scripts, () -> true, LOGGER).open(() -> "environment"));
        assertFalse(new ScriptCacheGuard(cache, scripts, () -> true, LOGGER).open(() -> "other"));
    }

    @Test
    void lockedCacheFilesDegradeToASessionInsteadOfThrowing() throws IOException {
        Path scripts = scripts();
        Path cache = sealed(scripts);
        Files.writeString(scripts.resolve("Recipe.groovy"), "changed");
        var discards = new AtomicInteger();
        var guard = new ScriptCacheGuard(cache, scripts, () -> {
            discards.incrementAndGet();
            return false;
        }, LOGGER);
        assertFalse(assertDoesNotThrow(() -> guard.open(() -> "environment")));
        assertEquals(1, discards.get());
        assertFalse(guard.persistent());
        assertDoesNotThrow(guard::afterWrite);
        // The unverifiable session never seals, so the original sources are not reauthorized either.
        Files.writeString(scripts.resolve("Recipe.groovy"), "version1");
        assertFalse(new ScriptCacheGuard(cache, scripts, () -> true, LOGGER).open(() -> "environment"));
    }

    @Test
    void sourceFingerprintFailuresNeverAuthorizeOrEscape() throws IOException {
        Path scripts = scripts();
        Path cache = sealed(scripts);
        var failures = new AtomicInteger();
        ScriptCacheGuard.SourceHasher loop = path -> {
            failures.incrementAndGet();
            throw new FileSystemLoopException(path.toString());
        };
        var discards = new AtomicInteger();
        var guard = new ScriptCacheGuard(cache, scripts, () -> discards.incrementAndGet() > 0, loop, LOGGER);
        assertFalse(assertDoesNotThrow(() -> guard.open(() -> "environment")));
        assertDoesNotThrow(guard::beforeRun);
        assertDoesNotThrow(guard::afterWrite);
        assertEquals(2, discards.get(), "open and each unverifiable stage must drop compiled classes");
        assertFalse(guard.persistent());
        assertTrue(failures.get() >= 2);
    }

    @Test
    void environmentAndSealFailuresAreNotFatal() throws IOException {
        Path scripts = scripts();
        Path cache = sealed(scripts);
        var unknown = new ScriptCacheGuard(cache, scripts, () -> true, LOGGER);
        assertFalse(assertDoesNotThrow(() -> unknown.open(() -> {
            throw new IOException("unreadable class source");
        })));
        assertFalse(unknown.persistent());
        // A regular file where the cache directory belongs makes sealing fail.
        Path blocked = Files.writeString(root.resolve("blocked"), "not a directory");
        var guard = new ScriptCacheGuard(blocked.resolve("cache"), scripts, () -> true, LOGGER);
        guard.open(() -> "environment");
        assertDoesNotThrow(guard::afterWrite);
        assertFalse(guard.persistent());
    }

    @Test
    void changedSourcesDuringASessionDiscardAndStillSeal() throws IOException {
        Path scripts = scripts();
        Path cache = sealed(scripts);
        var discards = new AtomicInteger();
        var guard = new ScriptCacheGuard(cache, scripts, () -> discards.incrementAndGet() > 0, LOGGER);
        assertTrue(guard.open(() -> "environment"));
        Files.writeString(scripts.resolve("Recipe.groovy"), "edited");
        guard.beforeRun();
        assertEquals(1, discards.get());
        guard.afterWrite();
        assertTrue(new ScriptCacheGuard(cache, scripts, () -> true, LOGGER).open(() -> "environment"));
    }

    private Path scripts() throws IOException {
        Path scripts = Files.createDirectories(root.resolve("groovy"));
        Files.writeString(scripts.resolve("Recipe.groovy"), "version1");
        return scripts;
    }

    private Path sealed(Path scripts) throws IOException {
        Path cache = Files.createDirectories(root.resolve("cache"));
        var guard = new ScriptCacheGuard(cache, scripts, () -> true, LOGGER);
        guard.open(() -> "environment");
        guard.afterWrite();
        assertTrue(new ScriptCacheGuard(cache, scripts, () -> true, LOGGER).open(() -> "environment"));
        return cache;
    }
}

package gregtech.integration.groovy.cache;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * Decides whether persisted Groovy bytecode may be reused, for one engine lifetime.
 * <p>
 * Fail-closed: bytecode from disk is reused only when sources, environment and compiled bytes all match a sealed
 * manifest. Any persistence failure (unreadable sources, a cache file locked by another process, a symlink loop, a
 * failed seal) degrades this engine to a session-only identity instead of throwing out of GroovyScript internals.
 * Scripts still compile and run; nothing is sealed, so the next start recompiles.
 */
public final class ScriptCacheGuard {

    private final Path cacheRoot;
    private final Path scriptRoot;
    private final BooleanSupplier discard;
    private final SourceHasher hasher;
    private final Logger logger;
    private ScriptCacheIdentity.Inputs inputs;
    private boolean persistent;

    /** Computes the content identity of a script tree. */
    @FunctionalInterface
    public interface SourceHasher {

        String hash(Path root) throws IOException;
    }

    /** @param discard clears in-memory and persisted engine caches; returns false when files could not be deleted */
    public ScriptCacheGuard(Path cacheRoot, Path scriptRoot, BooleanSupplier discard, Logger logger) {
        this(cacheRoot, scriptRoot, discard, ScriptCacheIdentity::sources, logger);
    }

    ScriptCacheGuard(Path cacheRoot, Path scriptRoot, BooleanSupplier discard, SourceHasher hasher, Logger logger) {
        this.cacheRoot = cacheRoot;
        this.scriptRoot = scriptRoot;
        this.discard = discard;
        this.hasher = hasher;
        this.logger = logger;
    }

    /**
     * Runs before the engine reads its persisted index.
     *
     * @return true only when the persisted index is verified and may be read; false means the caller must skip it
     */
    public boolean open(Callable<String> environment) {
        this.persistent = true;
        String captured;
        try {
            captured = environment.call();
        } catch (Exception unavailable) {
            // Unknown dependencies must never authorize old bytecode.
            captured = session();
            degrade("environment fingerprint unavailable", unavailable);
        }
        String sources;
        try {
            sources = hasher.hash(scriptRoot);
        } catch (IOException | RuntimeException unavailable) {
            sources = session();
            degrade("script sources could not be fingerprinted", unavailable);
        }
        this.inputs = new ScriptCacheIdentity.Inputs(sources, captured);
        if (persistent && ScriptCacheIdentity.reusable(cacheRoot, inputs)) {
            logger.info("GTCEu script cache: content/environment and compiled bytes verified");
            return true;
        }
        discard();
        logger.info("GTCEu script cache: content/environment check missed; recompilation required");
        return false;
    }

    /** Runs before each script stage; discards compiled classes whose authoring inputs changed. */
    public void beforeRun() {
        if (inputs == null) return;
        String sources;
        try {
            sources = hasher.hash(scriptRoot);
        } catch (IOException | RuntimeException unavailable) {
            // In-memory classes cannot be verified either, so drop them and recompile this stage.
            degrade("script sources could not be fingerprinted", unavailable);
            discard();
            this.inputs = new ScriptCacheIdentity.Inputs(session(), inputs.environment());
            return;
        }
        if (!sources.equals(inputs.sources())) {
            discard();
            this.inputs = new ScriptCacheIdentity.Inputs(sources, inputs.environment());
            logger.info("GTCEu script cache: authoring inputs changed; invalidated dependent classes");
        }
    }

    /** Runs after the engine writes its index; seals it only for verified, unchanged inputs. */
    public void afterWrite() {
        if (inputs == null) return;
        try {
            if (persistent && inputs.sources().equals(hasher.hash(scriptRoot))) {
                ScriptCacheIdentity.seal(cacheRoot, inputs);
                return;
            }
        } catch (IOException | RuntimeException failure) {
            degrade("compiled cache could not be sealed", failure);
        }
        try {
            ScriptCacheIdentity.invalidate(cacheRoot);
        } catch (IOException | RuntimeException failure) {
            // An unsealed cache is never reused: the manifest records sources, so a stale one cannot match.
            logger.warn("GTCEu script cache: could not remove the previous manifest", failure);
        }
    }

    /** Whether this engine may still seal its compiled classes for a later start. */
    public boolean persistent() {
        return persistent;
    }

    private void discard() {
        boolean deleted;
        try {
            deleted = discard.getAsBoolean();
        } catch (RuntimeException failure) {
            degrade("stale cache could not be discarded", failure);
            return;
        }
        if (!deleted) degrade("stale cache files could not be deleted", null);
    }

    private void degrade(String reason, Throwable cause) {
        if (persistent) {
            logger.warn("GTCEu script cache: {}; recompiling without persistent reuse for this session", reason,
                    cause);
        }
        this.persistent = false;
    }

    private static String session() {
        return "unverified-session:" + UUID.randomUUID();
    }
}

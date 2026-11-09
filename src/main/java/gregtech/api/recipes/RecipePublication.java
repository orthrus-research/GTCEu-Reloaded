package gregtech.api.recipes;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Publishes registration participants in order and, when a later step fails, restores every participant that was
 * already published, newest first. Each restore runs even if an earlier one failed; later failures are attached to
 * the original failure as suppressed exceptions so none is lost.
 */
final class RecipePublication {

    private final Deque<Runnable> restores = new ArrayDeque<>();

    /** Runs {@code publish}; {@code restore} is queued only once publication returned normally. */
    void publish(Runnable publish, Runnable restore) {
        publish.run();
        this.restores.push(restore);
    }

    /** Registers a participant that a caller already published. */
    void published(Runnable restore) {
        this.restores.push(restore);
    }

    /** Restores every published participant and returns {@code failure} carrying any restore failures. */
    RuntimeException rollback(RuntimeException failure) {
        while (!this.restores.isEmpty()) {
            try {
                this.restores.pop().run();
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
        }
        return failure;
    }
}

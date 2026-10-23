package gregtech.api.metatileentity;

/** Transient observations for one admitted output lane; never holds a handler or consumes recipe notifications. */
final class AutoOutputCache {

    private boolean dirty = true;
    private boolean empty;
    private boolean unavailable;
    private long lastProbeTick;

    void requestWakeup() {
        this.dirty = true;
    }

    boolean consumeWakeup() {
        boolean refresh = dirty;
        this.dirty = false;
        return refresh;
    }

    void refreshContents(boolean empty) {
        this.empty = empty;
        this.unavailable = false;
    }

    boolean shouldAttempt(long tick) {
        if (empty) return false;
        long elapsed = tick - lastProbeTick;
        // Called only on the machine's original five-tick phase. Clock rollback/overflow must not prolong a wait.
        return !unavailable || elapsed < 0 || elapsed >= 20;
    }

    void recordAttempt(boolean available, long tick) {
        this.unavailable = !available;
        this.lastProbeTick = tick;
    }
}

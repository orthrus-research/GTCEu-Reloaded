package gregtech.api.capability.impl;

import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import java.util.function.BooleanSupplier;

/**
 * The failure diagnostics of one recipe logic: the failure it shows, the failure of its last progress check, the
 * muffler gate, and the capture scope that keeps the first failure recorded inside one observed check.
 * <p>
 * It holds state only. {@link AbstractRecipeLogic} decides when a failure is set, cleared, recorded or captured, and
 * its methods for those stay the override points, so a subclass that overrides one still sees every call.
 */
final class RecipeFailureTracker {

    private RecipeFailure failure = RecipeFailure.NONE;
    private RecipeFailure progressFailure = RecipeFailure.NONE;
    private RecipeFailure captured = RecipeFailure.NONE;
    private boolean capturing;
    private boolean mufflerBlocked;

    /** @return the failure to show: an obstructed muffler ahead of the recorded failure */
    RecipeFailure get() {
        return mufflerBlocked ? RecipeFailure.of(RecipeFailureReason.MUFFLER_OBSTRUCTED) : failure;
    }

    void set(RecipeFailure failure) {
        this.failure = failure;
    }

    void clear() {
        this.failure = RecipeFailure.NONE;
    }

    /**
     * @return the failure of the last progress check, or the generic progress failure when that check recorded none
     */
    RecipeFailure getProgressFailure() {
        return progressFailure.isEmpty() ? RecipeFailure.of(RecipeFailureReason.PROGRESS_REQUIREMENTS) :
                progressFailure;
    }

    void setProgressFailure(RecipeFailure failure) {
        this.progressFailure = failure;
    }

    void clearProgressFailure() {
        this.progressFailure = RecipeFailure.NONE;
    }

    boolean isMufflerBlocked() {
        return mufflerBlocked;
    }

    void setMufflerBlocked(boolean blocked) {
        this.mufflerBlocked = blocked;
    }

    /** Forgets the progress failure, the capture scope and the muffler gate. The shown failure is cleared apart. */
    void resetDiagnostics() {
        this.progressFailure = RecipeFailure.NONE;
        this.captured = RecipeFailure.NONE;
        this.capturing = false;
        this.mufflerBlocked = false;
    }

    /**
     * Keeps the failure if a capture is open and has none yet; outside a capture it is dropped.
     *
     * @return false, so a caller can return it and keep its short-circuit order
     */
    boolean record(RecipeFailure failure) {
        if (capturing && captured.isEmpty()) this.captured = failure;
        return false;
    }

    /**
     * Runs the check exactly once inside its own capture. Captures nest: the enclosing capture is restored afterwards.
     *
     * @return no failure if the check passed, even when it recorded one; otherwise the first failure it recorded, or
     *         {@code unknownFailure} when it recorded none
     */
    RecipeFailure capture(BooleanSupplier check, RecipeFailureReason unknownFailure) {
        boolean previousCapturing = this.capturing;
        RecipeFailure previousFailure = this.captured;
        this.capturing = true;
        this.captured = RecipeFailure.NONE;
        try {
            if (check.getAsBoolean()) return RecipeFailure.NONE;
            return captured.isEmpty() ? RecipeFailure.of(unknownFailure) : captured;
        } finally {
            this.capturing = previousCapturing;
            this.captured = previousFailure;
        }
    }
}

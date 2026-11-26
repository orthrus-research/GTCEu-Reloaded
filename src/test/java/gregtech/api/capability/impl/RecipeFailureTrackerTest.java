package gregtech.api.capability.impl;

import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFailureTrackerTest {

    private static final RecipeFailure ENERGY = RecipeFailure.of(RecipeFailureReason.NOT_ENOUGH_ENERGY);
    private static final RecipeFailure CLEANROOM = RecipeFailure.of(RecipeFailureReason.CLEANROOM_DIRTY);

    @Test
    void showsTheSetFailureUntilItIsCleared() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        assertTrue(tracker.get().isEmpty());
        tracker.set(ENERGY);
        assertEquals(ENERGY, tracker.get());
        tracker.clear();
        assertTrue(tracker.get().isEmpty());
    }

    @Test
    void anObstructedMufflerIsShownAheadOfTheRecordedFailureWithoutReplacingIt() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        tracker.set(ENERGY);
        tracker.setMufflerBlocked(true);
        assertTrue(tracker.isMufflerBlocked());
        assertEquals(RecipeFailureReason.MUFFLER_OBSTRUCTED, tracker.get().getReason());
        tracker.setMufflerBlocked(false);
        assertEquals(ENERGY, tracker.get());
    }

    @Test
    void aFailureRecordedOutsideACaptureIsDropped() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        assertFalse(tracker.record(ENERGY));
        assertTrue(tracker.get().isEmpty());
        assertEquals(RecipeFailureReason.REQUIREMENTS,
                tracker.capture(() -> false, RecipeFailureReason.REQUIREMENTS).getReason());
    }

    @Test
    void aCaptureKeepsTheFirstRecordedFailureOfAFailedCheck() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        RecipeFailure captured = tracker.capture(() -> tracker.record(CLEANROOM) || tracker.record(ENERGY),
                RecipeFailureReason.REQUIREMENTS);
        assertEquals(CLEANROOM, captured);
        assertTrue(tracker.get().isEmpty(), "Capturing never publishes the failure");
    }

    @Test
    void aPassingCheckDiscardsWhatItRecorded() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        RecipeFailure captured = tracker.capture(() -> {
            tracker.record(CLEANROOM);
            return true;
        }, RecipeFailureReason.REQUIREMENTS);
        assertTrue(captured.isEmpty());
    }

    @Test
    void aFailedCheckThatRecordedNothingGetsTheUnknownReason() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        assertEquals(RecipeFailureReason.SETUP, tracker.capture(() -> false, RecipeFailureReason.SETUP).getReason());
    }

    @Test
    void aNestedCaptureRestoresTheEnclosingOne() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        RecipeFailure[] inner = new RecipeFailure[1];
        RecipeFailure outer = tracker.capture(() -> {
            inner[0] = tracker.capture(() -> tracker.record(ENERGY), RecipeFailureReason.REQUIREMENTS);
            return tracker.record(CLEANROOM);
        }, RecipeFailureReason.REQUIREMENTS);
        assertEquals(ENERGY, inner[0]);
        assertEquals(CLEANROOM, outer, "The inner capture's failure must not leak into the enclosing capture");
    }

    @Test
    void aCheckThatThrowsPropagatesAndLeavesLaterCapturesWorking() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        assertThrows(IllegalStateException.class, () -> tracker.capture(() -> {
            tracker.record(ENERGY);
            throw new IllegalStateException();
        }, RecipeFailureReason.REQUIREMENTS));
        assertEquals(RecipeFailureReason.SETUP, tracker.capture(() -> false, RecipeFailureReason.SETUP).getReason());
    }

    @Test
    void theProgressFailureFallsBackToTheGenericProgressReason() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        assertEquals(RecipeFailureReason.PROGRESS_REQUIREMENTS, tracker.getProgressFailure().getReason());
        tracker.setProgressFailure(CLEANROOM);
        assertEquals(CLEANROOM, tracker.getProgressFailure());
        tracker.clearProgressFailure();
        assertEquals(RecipeFailureReason.PROGRESS_REQUIREMENTS, tracker.getProgressFailure().getReason());
    }

    @Test
    void resettingTheDiagnosticsLeavesTheShownFailureToTheLogic() {
        RecipeFailureTracker tracker = new RecipeFailureTracker();
        tracker.set(ENERGY);
        tracker.setProgressFailure(CLEANROOM);
        tracker.setMufflerBlocked(true);
        tracker.resetDiagnostics();
        assertFalse(tracker.isMufflerBlocked());
        assertEquals(ENERGY, tracker.get());
        assertEquals(RecipeFailureReason.PROGRESS_REQUIREMENTS, tracker.getProgressFailure().getReason());
    }
}

package gregtech.api.recipes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipePublicationTest {

    @Test
    void failureAfterTheFirstParticipantPublishedRestoresItAndSkipsUnpublishedOnes() {
        List<String> events = new ArrayList<>();
        var publication = new RecipePublication();
        var failure = assertThrows(IllegalArgumentException.class, () -> {
            publication.publish(() -> events.add("publish furnace"), () -> events.add("restore furnace"));
            publication.publish(() -> {
                throw new IllegalArgumentException("crafting publication failed");
            }, () -> events.add("restore crafting"));
        });
        assertSame(failure, publication.rollback(failure));
        assertEquals(List.of("publish furnace", "restore furnace"), events);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void everyRestoreRunsNewestFirstAndLaterFailuresAreSuppressed() {
        List<String> events = new ArrayList<>();
        var publication = new RecipePublication();
        publication.publish(() -> events.add("publish furnace"), () -> {
            events.add("restore furnace");
            throw new IllegalStateException("furnace restore failed");
        });
        publication.published(() -> {
            events.add("restore crafting");
            throw new UnsupportedOperationException("crafting restore failed");
        });
        var failure = new IllegalStateException("recycling publication failed");
        publication.rollback(failure);
        assertEquals(List.of("publish furnace", "restore crafting", "restore furnace"), events);
        assertEquals(2, failure.getSuppressed().length);
        assertInstanceOf(UnsupportedOperationException.class, failure.getSuppressed()[0]);
        assertInstanceOf(IllegalStateException.class, failure.getSuppressed()[1]);
        // Rolled-back participants are not restored twice.
        publication.rollback(new IllegalStateException("again"));
        assertEquals(3, events.size());
    }
}

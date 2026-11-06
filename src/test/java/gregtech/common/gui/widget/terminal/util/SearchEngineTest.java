package gregtech.common.gui.widget.terminal.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SearchEngineTest {

    @Test
    void deliversOnlyTheLatestQueryOnTheOwnerThreadEvenWhenTheOldSearchIgnoresInterruption() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        List<String> executed = new ArrayList<>();
        List<String> received = new ArrayList<>();
        Thread owner = Thread.currentThread();
        try (SearchEngine<String> engine = new SearchEngine<>((word, result) -> {
            executed.add(word);
            if (word.equals("old")) {
                started.countDown();
                awaitIgnoringInterrupt(release);
            }
            result.accept(word);
            if (word.equals("latest")) finished.countDown();
        }, found -> {
            assertSame(owner, Thread.currentThread());
            received.add(found);
        })) {
            engine.searchWord("old");
            assertTrue(started.await(5, TimeUnit.SECONDS));
            engine.searchWord("superseded");
            engine.searchWord("latest");
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertTrue(received.isEmpty(), "Worker must not call the UI consumer");
            engine.drainResults();
            assertEquals(List.of("old", "latest"), executed);
            assertEquals(List.of("latest"), received);
        } finally {
            release.countDown();
        }
    }

    @Test
    void disposeDiscardsLateResultsAndRemainsReusable() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch late = new CountDownLatch(1);
        CountDownLatch fresh = new CountDownLatch(1);
        List<String> received = new ArrayList<>();
        try (SearchEngine<String> engine = new SearchEngine<>((word, result) -> {
            if (word.equals("old")) {
                started.countDown();
                awaitIgnoringInterrupt(release);
                result.accept(word);
                late.countDown();
            } else {
                result.accept(word);
                fresh.countDown();
            }
        }, received::add)) {
            engine.searchWord("old");
            assertTrue(started.await(5, TimeUnit.SECONDS));
            engine.dispose();
            release.countDown();
            assertTrue(late.await(5, TimeUnit.SECONDS));
            engine.drainResults();
            assertTrue(received.isEmpty());
            assertFalse(engine.isSearching());
            engine.searchWord("fresh");
            assertTrue(fresh.await(5, TimeUnit.SECONDS));
            engine.drainResults();
            assertEquals(List.of("fresh"), received);
        } finally {
            release.countDown();
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean done = false;
        while (!done) {
            try {
                done = latch.await(5, TimeUnit.SECONDS);
                if (!done) throw new AssertionError("Search fixture was not released");
            } catch (InterruptedException ignored) {
                // Deliberately model a third-party search which does not cooperate with cancellation.
            }
        }
    }
}

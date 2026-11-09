package mezz.jei.search;

import mezz.jei.util.Log;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AsyncCompletionTest {
    @AfterEach
    void closeService() {
        AsyncPrefixedSearchable.endService();
    }

    private static ExecutorService service() throws Exception {
        Field field = AsyncPrefixedSearchable.class.getDeclaredField("service");
        field.setAccessible(true);
        return (ExecutorService) field.get(null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Fixture barrier timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void absentServiceRunsCompletionImmediately() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPrefixedSearchable.afterBuild(calls::incrementAndGet);
        assertEquals(1, calls.get());
    }

    @Test
    void completionFollowsAllPreviouslyQueuedBuildWork() throws Exception {
        AsyncPrefixedSearchable.startService();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger stage = new AtomicInteger();
        service().submit(() -> { await(release); stage.set(1); });
        AsyncPrefixedSearchable.afterBuild(() -> { stage.compareAndSet(1, 2); completed.countDown(); });
        try {
            assertFalse(completed.await(50, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        AsyncPrefixedSearchable.endService();
        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertEquals(2, stage.get());
    }

    @Test
    void waitingForSearchTerminationDoesNotWaitForClientQueueCallbacks() throws Exception {
        for (int cycle = 0; cycle < 10; cycle++) {
            AsyncPrefixedSearchable.startService();
            Object clientQueue = new Object();
            CountDownLatch queueHeld = new CountDownLatch(1);
            CountDownLatch beginEnd = new CountDownLatch(1);
            CountDownLatch ended = new CountDownLatch(1);
            CountDownLatch callbackEntered = new CountDownLatch(1);
            CountDownLatch callbacksDone = new CountDownLatch(3);
            Thread client = new Thread(() -> {
                synchronized (clientQueue) {
                    queueHeld.countDown();
                    await(beginEnd);
                    AsyncPrefixedSearchable.endService();
                    ended.countDown();
                }
            }, "simulated-minecraft-client");
            client.start();
            boolean completedWithoutReleasingQueue;
            try {
                assertTrue(queueHeld.await(3, TimeUnit.SECONDS));
                for (int callback = 0; callback < 3; callback++) {
                    AsyncPrefixedSearchable.afterBuild(() -> {
                        callbackEntered.countDown();
                        synchronized (clientQueue) { callbacksDone.countDown(); }
                    });
                }
                assertTrue(callbackEntered.await(3, TimeUnit.SECONDS));
                beginEnd.countDown();
                completedWithoutReleasingQueue = ended.await(1, TimeUnit.SECONDS);
            } finally {
                beginEnd.countDown();
                if (client.isAlive()) client.interrupt();
                client.join(3000);
            }
            assertFalse(client.isAlive());
            assertTrue(completedWithoutReleasingQueue,
                    "Search termination waited for a callback needing the held client queue lock");
            assertTrue(callbacksDone.await(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void throwingCompletionIsLoggedRatherThanSwallowed() throws Exception {
        Logger logger = (Logger) Log.get();
        CountDownLatch logged = new CountDownLatch(1);
        AtomicReference<Throwable> reported = new AtomicReference<>();
        Appender capture = new AbstractAppender("hei-after-build-capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.ERROR &&
                        "HEI afterBuild callback failed".equals(event.getMessage().getFormattedMessage())) {
                    reported.set(event.getThrown());
                    logged.countDown();
                }
            }
        };
        capture.start();
        logger.addAppender(capture);
        try {
            AsyncPrefixedSearchable.startService();
            RuntimeException failure = new RuntimeException("callback failure");
            AsyncPrefixedSearchable.afterBuild(() -> { throw failure; });
            assertTrue(logged.await(3, TimeUnit.SECONDS), "afterBuild callback failure was not logged");
            Throwable thrown = reported.get();
            assertSame(failure, thrown instanceof CompletionException ? thrown.getCause() : thrown);
        } finally {
            logger.removeAppender(capture);
            capture.stop();
        }
    }

    @Test
    void replacingServiceDropsCompletionQueuedBehindCancelledBuild() throws Exception {
        AsyncPrefixedSearchable.startService();
        CountDownLatch buildEntered = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        AtomicInteger oldCalls = new AtomicInteger();
        service().submit(() -> { buildEntered.countDown(); await(releaseBuild); });
        assertTrue(buildEntered.await(3, TimeUnit.SECONDS));
        AsyncPrefixedSearchable.afterBuild(oldCalls::incrementAndGet);
        AsyncPrefixedSearchable.startService();
        releaseBuild.countDown();
        CountDownLatch newCompleted = new CountDownLatch(1);
        AsyncPrefixedSearchable.afterBuild(newCompleted::countDown);
        AsyncPrefixedSearchable.endService();
        assertTrue(newCompleted.await(3, TimeUnit.SECONDS));
        assertEquals(0, oldCalls.get());
    }
}

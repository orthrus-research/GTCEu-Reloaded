package gregtech.api.recipes;

import gregtech.api.util.GTLog;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RecipeCallbackLogTest {

    @Test
    void eachSubjectIsLoggedOnceWithItsException() {
        Logger logger = (Logger) GTLog.logger;
        List<String> messages = new CopyOnWriteArrayList<>();
        List<Throwable> thrown = new CopyOnWriteArrayList<>();
        @SuppressWarnings("deprecation")
        AbstractAppender appender = new AbstractAppender("RecipeCallbackLogTest", null, null, false) {

            @Override
            public void append(LogEvent event) {
                if (event.getLevel() != Level.WARN) return;
                messages.add(event.getMessage().getFormattedMessage());
                thrown.add(event.getThrown());
            }
        };
        appender.start();
        // Without a test configuration log4j falls back to ERROR, which would drop the warning before appenders
        Level previousLevel = logger.isEnabled(Level.WARN) ? null : logger.getLevel();
        if (previousLevel != null) logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            RuntimeException first = new IllegalStateException("first");
            RecipeCallbackLog.warnOnce("Recipe condition test:once", first);
            RecipeCallbackLog.warnOnce("Recipe condition test:once", new IllegalStateException("second"));
            RecipeCallbackLog.warnOnce("Recipe condition test:other", new IllegalStateException("third"));

            assertEquals(2, messages.size());
            assertEquals("Recipe condition test:once threw; machines report it as unavailable or invalid, and its " +
                    "later failures are not logged", messages.get(0));
            assertSame(first, thrown.get(0));
        } finally {
            logger.removeAppender(appender);
            if (previousLevel != null) logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}

package gregtech.api.recipes;

import gregtech.api.util.GTLog;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs a recipe condition, modifier or their saved data that threw, once for each subject, with the stack trace.
 * Machines still show only their bounded diagnostics; this log is where the cause can be found.
 */
public final class RecipeCallbackLog {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private RecipeCallbackLog() {}

    /**
     * @param subject   what threw, such as {@code "Recipe condition gregtech:dimension"}; later failures with the same
     *                  subject are not logged
     * @param exception what it threw
     */
    public static void warnOnce(@NotNull String subject, @NotNull RuntimeException exception) {
        if (LOGGED.add(subject)) {
            GTLog.logger.warn("{} threw; machines report it as unavailable or invalid, and its later failures are " +
                    "not logged", subject, exception);
        }
    }
}

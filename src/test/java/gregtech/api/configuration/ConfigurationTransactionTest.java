package gregtech.api.configuration;

import gregtech.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationTransactionTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void checksEverySnapshotBeforeAnyMutationAndExecutesOnlyOnce() {
        List<String> events = new ArrayList<>();
        ConfigurationTransaction transaction = new ConfigurationTransaction();
        transaction.add(change(events, "first", true, false));
        transaction.add(change(events, "stale", false, false));
        assertFalse(transaction.execute());
        assertEquals(Arrays.asList("check first", "check stale"), events);
        assertThrows(IllegalStateException.class, transaction::execute);
        assertThrows(IllegalStateException.class, () -> transaction.guard(() -> true));
    }

    @Test
    public void deniedGuardPreventsAllMutations() {
        List<String> events = new ArrayList<>();
        ConfigurationTransaction transaction = new ConfigurationTransaction();
        transaction.add(change(events, "untouched", true, false));
        transaction.guard(() -> false);
        assertFalse(transaction.execute());
        assertTrue(events.isEmpty());
    }

    @Test
    public void rollsBackPartiallyAppliedFailureThenEarlierChangesInReverseOrder() {
        List<String> events = new ArrayList<>();
        ConfigurationTransaction transaction = new ConfigurationTransaction();
        transaction.add(change(events, "first", true, false));
        transaction.add(change(events, "failing", true, true));
        transaction.add(change(events, "later", true, false));
        assertFalse(transaction.execute());
        assertEquals(Arrays.asList("check first", "check failing", "check later", "apply first", "apply failing",
                "restore failing", "restore first"), events);
    }

    private static ConfigurationChange change(List<String> events, String name, boolean current, boolean fails) {
        return new ConfigurationChange() {

            @Override
            public boolean isCurrent() {
                events.add("check " + name);
                return current;
            }

            @Override
            public void apply() {
                events.add("apply " + name);
                if (fails) throw new IllegalStateException("Injected partial failure");
            }

            @Override
            public void rollback() {
                events.add("restore " + name);
            }
        };
    }
}

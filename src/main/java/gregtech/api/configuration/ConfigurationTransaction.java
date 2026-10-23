package gregtech.api.configuration;

import gregtech.api.util.GTLog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Prepared and executed synchronously on the server thread; no resources are extracted or inserted. */
public final class ConfigurationTransaction {

    private final List<BooleanSupplier> guards = new ArrayList<>();
    private final List<ConfigurationChange> changes = new ArrayList<>();
    private boolean executed;

    public void guard(BooleanSupplier guard) {
        if (executed) throw new IllegalStateException("Transaction already executed");
        guards.add(guard);
    }

    public void add(ConfigurationChange change) {
        if (executed) throw new IllegalStateException("Transaction already executed");
        changes.add(change);
    }

    public boolean execute() {
        if (executed) throw new IllegalStateException("Transaction already executed");
        this.executed = true;
        for (BooleanSupplier guard : guards) {
            if (!guard.getAsBoolean()) return false;
        }
        for (ConfigurationChange change : changes) {
            if (!change.isCurrent()) return false;
        }
        int attempted = -1;
        try {
            for (int i = 0; i < changes.size(); i++) {
                attempted = i;
                changes.get(i).apply();
            }
            return true;
        } catch (RuntimeException failure) {
            for (int i = attempted; i >= 0; i--) {
                try {
                    changes.get(i).rollback();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            GTLog.logger.error("Configuration paste failed; attempted to restore every changed setting", failure);
            return false;
        }
    }
}

package gregtech.api.configuration;

/** One reversible settings change. Rollback must also work if apply throws after a partial change. */
public interface ConfigurationChange {

    boolean isCurrent();

    void apply();

    void rollback();
}

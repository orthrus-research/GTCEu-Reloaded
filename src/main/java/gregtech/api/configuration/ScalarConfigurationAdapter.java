package gregtech.api.configuration;

import net.minecraft.nbt.NBTTagCompound;

/** A settings snapshot whose validation and setters operate only on one object. */
public abstract class ScalarConfigurationAdapter implements ConfigurationAdapter {

    private final String schema;

    protected ScalarConfigurationAdapter(String schema) {
        this.schema = schema;
    }

    @Override
    public final String getSchema() {
        return schema;
    }

    protected abstract void validate(NBTTagCompound settings);

    protected abstract void apply(NBTTagCompound settings);

    @Override
    public final void prepare(NBTTagCompound settings, ConfigurationContext context,
                              ConfigurationTransaction transaction) {
        validate(settings);
        NBTTagCompound desired = settings.copy();
        NBTTagCompound before = capture();
        validate(before);
        transaction.add(new ConfigurationChange() {

            @Override
            public boolean isCurrent() {
                return before.equals(capture());
            }

            @Override
            public void apply() {
                ScalarConfigurationAdapter.this.apply(desired);
                ConfigurationData.require(desired.equals(capture()));
            }

            @Override
            public void rollback() {
                ScalarConfigurationAdapter.this.apply(before);
                ConfigurationData.require(before.equals(capture()));
            }
        });
    }
}

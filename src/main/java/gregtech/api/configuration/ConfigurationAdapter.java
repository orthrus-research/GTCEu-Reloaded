package gregtech.api.configuration;

import net.minecraft.nbt.NBTTagCompound;

/**
 * An opt-in, settings-only contract for the configuration card. Implementations must validate without mutation,
 * preserve resources and runtime state, and supply changes which can restore their captured state after failure.
 */
public interface ConfigurationAdapter {

    String getSchema();

    NBTTagCompound capture();

    void prepare(NBTTagCompound settings, ConfigurationContext context, ConfigurationTransaction transaction);
}

package gregtech.api.configuration;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import java.util.HashMap;
import java.util.Map;

final class PipeTestEndpoint extends TileEntity {

    private final Map<Capability<?>, Object> capabilities = new HashMap<>();
    boolean enabled = true;

    <T> PipeTestEndpoint provide(Capability<T> capability, T value) {
        this.capabilities.put(capability, value);
        return this;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing side) {
        return this.enabled && this.capabilities.containsKey(capability);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        Object value = this.enabled ? this.capabilities.get(capability) : null;
        return value == null ? null : (T) value;
    }
}

package gregtech.integration.probe;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** A producer for tile entities that expose a capability, on the unsided face. */
public abstract class CapabilityProbeInfoProducer<T> implements ProbeInfoProducer {

    protected abstract Capability<T> getCapability();

    protected boolean allowDisplaying(T capability) {
        return true;
    }

    protected abstract void addInfo(T capability, ProbeContext context, List<ProbeLine> lines);

    @Override
    public final void addInfo(ProbeContext context, List<ProbeLine> lines) {
        TileEntity tileEntity = context.tileEntity();
        if (tileEntity == null) return;
        T capability = tileEntity.getCapability(getCapability(), null);
        if (capability != null && allowDisplaying(capability)) {
            addInfo(capability, context, lines);
        }
    }
}

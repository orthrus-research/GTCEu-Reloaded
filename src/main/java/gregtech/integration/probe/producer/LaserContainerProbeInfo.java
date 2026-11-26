package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.ILaserContainer;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;

import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** The energy a laser container stores, as a bar like other energy containers'. */
public class LaserContainerProbeInfo extends CapabilityProbeInfoProducer<ILaserContainer> {

    @Override
    protected Capability<ILaserContainer> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_LASER;
    }

    @Override
    protected boolean allowDisplaying(ILaserContainer capability) {
        return !capability.isOneProbeHidden();
    }

    @Override
    protected void addInfo(ILaserContainer capability, ProbeContext context, List<ProbeLine> lines) {
        // The One Probe has always shown a laser container's numbers ungrouped
        EnergyContainerProbeInfo.addLines(capability.getEnergyStored(), capability.getEnergyCapacity(), false, lines);
    }
}

package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.util.TextFormattingUtil;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeProgress;
import gregtech.integration.probe.ProbeText;

import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** The energy a container stores, as a bar; laser containers have {@link LaserContainerProbeInfo}. */
public class EnergyContainerProbeInfo extends CapabilityProbeInfoProducer<IEnergyContainer> {

    private static final int ENERGY_COLOR = 0xFFEEE600;

    @Override
    protected Capability<IEnergyContainer> getCapability() {
        return GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER;
    }

    @Override
    protected boolean allowDisplaying(IEnergyContainer capability) {
        return !capability.isOneProbeHidden() && !(capability instanceof ILaserContainer);
    }

    @Override
    protected void addInfo(IEnergyContainer capability, ProbeContext context, List<ProbeLine> lines) {
        addLines(capability.getEnergyStored(), capability.getEnergyCapacity(), true, lines);
    }

    /** Adds the bar, its numbers grouped in thousands or not, unless the container holds no energy at all. */
    static void addLines(long stored, long capacity, boolean grouped, List<ProbeLine> lines) {
        if (capacity == 0) return;
        ProbeText fallback = ProbeText.translation("gregtech.waila.energy_stored",
                TextFormattingUtil.formatNumbers(stored), TextFormattingUtil.formatNumbers(capacity));
        lines.add(ProbeLine.progress(new ProbeProgress(stored, capacity, "EU", grouped, true, ENERGY_COLOR, fallback)));
    }
}

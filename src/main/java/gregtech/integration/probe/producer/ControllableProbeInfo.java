package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** Warns when a machine's work is disabled. */
public class ControllableProbeInfo extends CapabilityProbeInfoProducer<IControllable> {

    @Override
    protected Capability<IControllable> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE;
    }

    @Override
    protected void addInfo(IControllable capability, ProbeContext context, List<ProbeLine> lines) {
        addLines(capability.isWorkingEnabled(), lines);
    }

    static void addLines(boolean workingEnabled, List<ProbeLine> lines) {
        if (workingEnabled) return;
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.WARNING)
                .translation("gregtech.top.working_disabled")
                .build()));
    }
}

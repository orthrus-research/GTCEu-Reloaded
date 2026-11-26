package gregtech.integration.probe.producer;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeText;

import java.util.List;

/** Whether a multiblock part that joins an ME network is online. */
public class AEHatchProbeInfo implements ProbeInfoProducer {

    @Override
    public void addInfo(ProbeContext context, List<ProbeLine> lines) {
        if (context.metaTileEntity() instanceof MetaTileEntityAEHostablePart<?> part) addLines(part.isOnline(), lines);
    }

    static void addLines(boolean online, List<ProbeLine> lines) {
        String key = online ? "gregtech.gui.me_network.online" : "gregtech.gui.me_network.offline";
        lines.add(ProbeLine.text(ProbeText.translation(key)));
    }
}

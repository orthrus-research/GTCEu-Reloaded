package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** Whether a multiblock's structure is formed, and whether it is obstructed. */
public class MultiblockProbeInfo extends CapabilityProbeInfoProducer<IMultiblockController> {

    @Override
    protected Capability<IMultiblockController> getCapability() {
        return GregtechCapabilities.CAPABILITY_MULTIBLOCK_CONTROLLER;
    }

    @Override
    protected void addInfo(IMultiblockController capability, ProbeContext context, List<ProbeLine> lines) {
        addLines(capability.isStructureFormed(), capability.isStructureObstructed(), lines);
    }

    static void addLines(boolean formed, boolean obstructed, List<ProbeLine> lines) {
        if (!formed) {
            lines.add(line(ProbeText.format(TextFormatting.RED), "gregtech.top.invalid_structure"));
            return;
        }

        lines.add(line(ProbeText.style(ProbeStyle.OK), "gregtech.top.valid_structure"));
        if (obstructed) lines.add(line(ProbeText.format(TextFormatting.RED), "gregtech.top.obstructed_structure"));
    }

    private static ProbeLine line(ProbeText start, String key) {
        return ProbeLine.text(ProbeText.builder().append(start).translation(key).build());
    }
}

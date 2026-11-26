package gregtech.integration.probe.producer;

import gregtech.common.blocks.BlockLamp;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeText;

import net.minecraft.block.state.IBlockState;

import java.util.List;

/** A lamp's settings that differ from a plain lamp's. */
public class LampProbeInfo implements ProbeInfoProducer {

    @Override
    public void addInfo(ProbeContext context, List<ProbeLine> lines) {
        IBlockState state = context.state();
        if (state.getBlock() instanceof BlockLamp lamp) {
            addLines(lamp.isInverted(state), lamp.isBloomEnabled(state), lamp.isLightEnabled(state), lines);
        }
    }

    static void addLines(boolean inverted, boolean bloomEnabled, boolean lightEnabled, List<ProbeLine> lines) {
        if (inverted) lines.add(ProbeLine.text(ProbeText.translation("tile.gregtech_lamp.tooltip.inverted")));
        if (!bloomEnabled) lines.add(ProbeLine.text(ProbeText.translation("tile.gregtech_lamp.tooltip.no_bloom")));
        if (!lightEnabled) lines.add(ProbeLine.text(ProbeText.translation("tile.gregtech_lamp.tooltip.no_light")));
    }
}

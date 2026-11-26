package gregtech.integration.probe.producer;

import gregtech.api.metatileentity.multiblock.IPrimitivePump;
import gregtech.api.util.TextFormattingUtil;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;

import java.util.List;

/** The water a primitive pump produces each second. */
public class PrimitivePumpProbeInfo implements ProbeInfoProducer {

    @Override
    public void addInfo(ProbeContext context, List<ProbeLine> lines) {
        if (context.metaTileEntity() instanceof IPrimitivePump pump) addLines(pump.getFluidProduction(), lines);
    }

    static void addLines(int production, List<ProbeLine> lines) {
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation("gregtech.top.primitive_pump_production")
                .literal(" ")
                .format(TextFormatting.AQUA)
                .literal(TextFormattingUtil.formatNumbers(production))
                .format(TextFormatting.RESET)
                .literal(" L/s")
                .build()));
    }
}

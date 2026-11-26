package gregtech.integration.probe.producer;

import gregtech.api.capability.FeCompat;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.converter.ConverterTrait;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** An energy converter's direction and, on the side looked at, the EU or FE it takes or gives. */
public class ConverterProbeInfo extends CapabilityProbeInfoProducer<ConverterTrait> {

    @Override
    protected Capability<ConverterTrait> getCapability() {
        return GregtechCapabilities.CAPABILITY_CONVERTER;
    }

    @Override
    protected void addInfo(ConverterTrait capability, ProbeContext context, List<ProbeLine> lines) {
        addLines(capability.isFeToEu(), capability.getVoltage(), capability.getBaseAmps(),
                capability.getMetaTileEntity().getFrontFacing(), lines);
    }

    static void addLines(boolean feToEu, long voltage, int amperage, EnumFacing front, List<ProbeLine> lines) {
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation(feToEu ? "gregtech.top.convert_fe" : "gregtech.top.convert_eu")
                .build()));

        // the front gives the converted energy; every other side takes energy in
        ProbeText eu = TransformerProbeInfo.energy(voltage, amperage);
        int feAmount = FeCompat.toFe(voltage * amperage, FeCompat.ratio(feToEu));
        ProbeText fe = ProbeText.builder()
                .format(TextFormatting.RED)
                .literal(TextFormattingUtil.formatNumbers(feAmount) + " FE")
                .build();
        int frontSide = 1 << front.getIndex();
        lines.add(sideLine("gregtech.top.transform_output", feToEu ? eu : fe).onSides(frontSide));
        lines.add(sideLine("gregtech.top.transform_input", feToEu ? fe : eu).onSides(ProbeLine.ALL_SIDES & ~frontSide));
    }

    private static ProbeLine sideLine(String key, ProbeText energy) {
        return ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation(key)
                .literal(" ")
                .append(energy)
                .build());
    }
}

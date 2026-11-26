package gregtech.integration.probe.producer;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.electric.MetaTileEntityTransformer;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** A transformer's step and, on the side looked at, its input or its output. */
public class TransformerProbeInfo extends CapabilityProbeInfoProducer<IEnergyContainer> {

    @Override
    protected Capability<IEnergyContainer> getCapability() {
        return GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER;
    }

    @Override
    protected boolean allowDisplaying(IEnergyContainer capability) {
        return !capability.isOneProbeHidden();
    }

    @Override
    protected void addInfo(IEnergyContainer capability, ProbeContext context, List<ProbeLine> lines) {
        if (!(context.metaTileEntity() instanceof MetaTileEntityTransformer transformer)) return;
        int inputSides = ProbeLine.sides(capability::inputsEnergy);
        int outputSides = ProbeLine.sides(capability::outputsEnergy) & ~inputSides;
        ProbeText input = energy(capability.getInputVoltage(), capability.getInputAmperage());
        ProbeText output = energy(capability.getOutputVoltage(), capability.getOutputAmperage());
        addLines(transformer.isInverted(), input, output, inputSides, outputSides, lines);
    }

    /** A voltage tier with its amperage, such as {@code LV (1A)}. */
    static ProbeText energy(long voltage, long amperage) {
        return ProbeText.builder()
                .literal(GTValues.VNF[GTUtility.getTierByVoltage(voltage)])
                .format(TextFormatting.GREEN)
                .literal(" (" + TextFormattingUtil.formatNumbers(amperage) + "A)")
                .build();
    }

    static void addLines(boolean stepUp, ProbeText input, ProbeText output, int inputSides, int outputSides,
                         List<ProbeLine> lines) {
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .format(stepUp ? TextFormatting.RED : TextFormatting.GREEN)
                .translation(stepUp ? "gregtech.top.transform_up" : "gregtech.top.transform_down")
                .literal(" ")
                .format(TextFormatting.RESET)
                .append(input)
                .literal(" -> ")
                .append(output)
                .build()));
        lines.add(sideLine(TextFormatting.GOLD, "gregtech.top.transform_input", input).onSides(inputSides));
        lines.add(sideLine(TextFormatting.BLUE, "gregtech.top.transform_output", output).onSides(outputSides));
    }

    /** An input or output line: its label in a colour, then the energy it carries. */
    static ProbeLine sideLine(TextFormatting color, String key, ProbeText energy) {
        return ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .format(color)
                .translation(key)
                .literal(" ")
                .format(TextFormatting.RESET)
                .append(energy)
                .build());
    }
}

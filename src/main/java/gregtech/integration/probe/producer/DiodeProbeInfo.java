package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.electric.MetaTileEntityDiode;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** A diode's input or output amperage, on the side looked at. */
public class DiodeProbeInfo extends CapabilityProbeInfoProducer<IEnergyContainer> {

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
        if (!(context.metaTileEntity() instanceof MetaTileEntityDiode)) return;
        int inputSides = ProbeLine.sides(capability::inputsEnergy);
        int outputSides = ProbeLine.sides(capability::outputsEnergy) & ~inputSides;
        addLines(capability.getInputAmperage(), capability.getOutputAmperage(), inputSides, outputSides, lines);
    }

    static void addLines(long inputAmperage, long outputAmperage, int inputSides, int outputSides,
                         List<ProbeLine> lines) {
        lines.add(TransformerProbeInfo.sideLine(TextFormatting.GOLD, "gregtech.top.transform_input",
                amperage(inputAmperage)).onSides(inputSides));
        lines.add(TransformerProbeInfo.sideLine(TextFormatting.BLUE, "gregtech.top.transform_output",
                amperage(outputAmperage)).onSides(outputSides));
    }

    private static ProbeText amperage(long amperage) {
        return ProbeText.literal(TextFormattingUtil.formatNumbers(amperage) + " A");
    }
}

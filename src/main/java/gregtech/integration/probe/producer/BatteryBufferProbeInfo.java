package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.common.metatileentities.electric.MetaTileEntityBatteryBuffer;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** A battery buffer's average input and output per tick over the last second. */
public class BatteryBufferProbeInfo extends CapabilityProbeInfoProducer<IEnergyContainer> {

    @Override
    protected Capability<IEnergyContainer> getCapability() {
        return GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER;
    }

    @Override
    protected void addInfo(IEnergyContainer capability, ProbeContext context, List<ProbeLine> lines) {
        if (!(context.metaTileEntity() instanceof MetaTileEntityBatteryBuffer)) return;
        addLines(capability.getInputPerSec() / 20, capability.getOutputPerSec() / 20, lines);
    }

    static void addLines(long averageInput, long averageOutput, List<ProbeLine> lines) {
        lines.add(averageLine(TextFormatting.GREEN, "gregtech.battery_buffer.average_input.top", averageInput));
        lines.add(averageLine(TextFormatting.RED, "gregtech.battery_buffer.average_output.top", averageOutput));
    }

    /** A label in a colour, followed by an average energy rate in white. */
    static ProbeLine averageLine(TextFormatting color, String key, long average) {
        ProbeText rate = ProbeText.builder().format(TextFormatting.WHITE).quantity(average, "EU/t").build();
        return ProbeLine.text(ProbeText.builder().format(color).translation(key, rate).build());
    }
}

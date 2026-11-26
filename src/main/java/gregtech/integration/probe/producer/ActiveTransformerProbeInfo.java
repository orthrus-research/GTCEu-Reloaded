package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityActiveTransformer;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** A working active transformer's average throughput over the last second. */
public class ActiveTransformerProbeInfo extends CapabilityProbeInfoProducer<IMultiblockController> {

    @Override
    protected Capability<IMultiblockController> getCapability() {
        return GregtechCapabilities.CAPABILITY_MULTIBLOCK_CONTROLLER;
    }

    @Override
    protected boolean allowDisplaying(IMultiblockController capability) {
        return capability instanceof MetaTileEntityActiveTransformer activeTransformer && activeTransformer.isActive();
    }

    @Override
    protected void addInfo(IMultiblockController capability, ProbeContext context, List<ProbeLine> lines) {
        addLines(((MetaTileEntityActiveTransformer) capability).getAverageIOLastSec(), lines);
    }

    static void addLines(long averageIO, List<ProbeLine> lines) {
        lines.add(BatteryBufferProbeInfo.averageLine(TextFormatting.AQUA,
                "gregtech.multiblock.active_transformer.average_io", averageIO));
    }
}

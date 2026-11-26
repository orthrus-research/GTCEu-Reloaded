package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.ComputationRecipeLogic;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeProgress;
import gregtech.integration.probe.ProbeText;

import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/**
 * The progress of an active machine's work: in ticks below a second, in seconds from there, or in computation
 * units for recipes that show computation instead of duration.
 */
public class WorkableProbeInfo extends CapabilityProbeInfoProducer<IWorkable> {

    private static final int WORKING_COLOR = 0xFF4CBB17;
    private static final int COMPUTATION_COLOR = 0xFF00D4CE;
    private static final int DISABLED_COLOR = 0xFFBB1C28;

    @Override
    protected Capability<IWorkable> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_WORKABLE;
    }

    @Override
    protected void addInfo(IWorkable capability, ProbeContext context, List<ProbeLine> lines) {
        if (!capability.isActive()) return;
        boolean computation = capability instanceof ComputationRecipeLogic logic && !logic.shouldShowDuration();
        addLines(computation, capability.isWorkingEnabled(), capability.getProgress(), capability.getMaxProgress(),
                lines);
    }

    static void addLines(boolean computation, boolean workingEnabled, int progress, int maxProgress,
                         List<ProbeLine> lines) {
        if (computation) {
            ProbeText fallback = ProbeText.translation("gregtech.waila.progress_computation", progress, maxProgress);
            lines.add(ProbeLine.progress(new ProbeProgress(progress, maxProgress, "CWU", false, false,
                    workingEnabled ? COMPUTATION_COLOR : DISABLED_COLOR, fallback)));
            return;
        }

        String unit = "t";
        String fallbackKey = "gregtech.waila.progress_tick";
        if (maxProgress >= 20) {
            progress = Math.round(progress / 20.0F);
            maxProgress = Math.round(maxProgress / 20.0F);
            unit = "s";
            fallbackKey = "gregtech.waila.progress_sec";
        }
        if (maxProgress <= 0) return;

        ProbeText fallback = ProbeText.translation(fallbackKey, progress, maxProgress);
        lines.add(ProbeLine.progress(new ProbeProgress(progress, maxProgress, unit, true, false,
                workingEnabled ? WORKING_COLOR : DISABLED_COLOR, fallback)));
    }
}

package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.recipes.RecipeMap;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraftforge.common.capabilities.Capability;

import java.util.ArrayList;
import java.util.List;

/** The modes of a machine with more than one recipe map, marking the current one. */
public class MultiRecipeMapProbeInfo extends CapabilityProbeInfoProducer<IMultipleRecipeMaps> {

    @Override
    protected Capability<IMultipleRecipeMaps> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_MULTIPLE_RECIPEMAPS;
    }

    @Override
    protected void addInfo(IMultipleRecipeMaps capability, ProbeContext context, List<ProbeLine> lines) {
        RecipeMap<?> current = capability.getCurrentRecipeMap();
        List<String> modeKeys = new ArrayList<>();
        int currentMode = -1;
        for (RecipeMap<?> recipeMap : capability.getAvailableRecipeMaps()) {
            if (currentMode < 0 && recipeMap.equals(current)) currentMode = modeKeys.size();
            modeKeys.add(recipeMap.getTranslationKey());
        }
        addLines(modeKeys, currentMode, lines);
    }

    static void addLines(List<String> modeKeys, int currentMode, List<ProbeLine> lines) {
        if (modeKeys.size() == 1) return;

        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation("gregtech.multiblock.multiple_recipemaps.header")
                .build()));
        for (int mode = 0; mode < modeKeys.size(); mode++) {
            ProbeText.Builder line = ProbeText.builder().literal("   ");
            if (mode == currentMode) {
                line.style(ProbeStyle.INFO_IMPORTANT).translation(modeKeys.get(mode)).literal(" <");
            } else {
                line.style(ProbeStyle.LABEL).translation(modeKeys.get(mode));
            }
            lines.add(ProbeLine.text(line.build()));
        }
    }
}

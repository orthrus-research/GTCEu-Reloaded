package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiRecipeMapProbeInfoTest {

    private static List<String> lines(List<String> modeKeys, int currentMode) {
        List<ProbeLine> lines = new ArrayList<>();
        MultiRecipeMapProbeInfo.addLines(modeKeys, currentMode, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void listsModesAndMarksTheCurrentOne() {
        assertEquals(List.of("<info>[gregtech.multiblock.multiple_recipemaps.header]",
                "   <label>[recipemap.a.name]",
                "   <info_important>[recipemap.b.name] <"),
                lines(List.of("recipemap.a.name", "recipemap.b.name"), 1));
    }

    @Test
    public void aSingleModeIsNotListed() {
        assertEquals(List.of(), lines(List.of("recipemap.a.name"), 0));
    }
}

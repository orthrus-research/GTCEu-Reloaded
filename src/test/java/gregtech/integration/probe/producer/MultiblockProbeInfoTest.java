package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiblockProbeInfoTest {

    private static List<String> lines(boolean formed, boolean obstructed) {
        List<ProbeLine> lines = new ArrayList<>();
        MultiblockProbeInfo.addLines(formed, obstructed, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void formedStructureIsOk() {
        assertEquals(List.of("<ok>[gregtech.top.valid_structure]"), lines(true, false));
    }

    @Test
    public void obstructionFollowsTheFormedLineInRed() {
        assertEquals(List.of("<ok>[gregtech.top.valid_structure]", "&c[gregtech.top.obstructed_structure]"),
                lines(true, true));
    }

    @Test
    public void incompleteStructureIsRed() {
        assertEquals(List.of("&c[gregtech.top.invalid_structure]"), lines(false, true));
    }
}

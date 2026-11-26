package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ControllableProbeInfoTest {

    private static List<String> lines(boolean workingEnabled) {
        List<ProbeLine> lines = new ArrayList<>();
        ControllableProbeInfo.addLines(workingEnabled, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void warnsWhenWorkingIsDisabled() {
        assertEquals(List.of("<warning>[gregtech.top.working_disabled]"), lines(false));
    }

    @Test
    public void addsNothingWhileWorkingIsEnabled() {
        assertEquals(List.of(), lines(true));
    }
}

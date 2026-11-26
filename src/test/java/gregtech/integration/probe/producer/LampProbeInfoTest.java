package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LampProbeInfoTest {

    private static List<String> lines(boolean inverted, boolean bloomEnabled, boolean lightEnabled) {
        List<ProbeLine> lines = new ArrayList<>();
        LampProbeInfo.addLines(inverted, bloomEnabled, lightEnabled, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void aPlainLampShowsNothing() {
        assertEquals(List.of(), lines(false, true, true));
    }

    @Test
    public void listsEachChangedSetting() {
        assertEquals(List.of("[tile.gregtech_lamp.tooltip.inverted]", "[tile.gregtech_lamp.tooltip.no_bloom]",
                "[tile.gregtech_lamp.tooltip.no_light]"), lines(true, false, false));
    }
}

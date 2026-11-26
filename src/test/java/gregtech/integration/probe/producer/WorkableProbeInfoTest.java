package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeProgress;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkableProbeInfoTest {

    private static List<ProbeLine> produce(boolean computation, boolean workingEnabled, int progress, int max) {
        List<ProbeLine> lines = new ArrayList<>();
        WorkableProbeInfo.addLines(computation, workingEnabled, progress, max, lines);
        return lines;
    }

    private static ProbeProgress bar(List<ProbeLine> lines) {
        assertEquals(1, lines.size());
        return Objects.requireNonNull(lines.get(0).getProgress());
    }

    @Test
    public void shortRecipesCountTicks() {
        List<ProbeLine> lines = produce(false, true, 3, 10);

        assertEquals(List.of("bar 3/10 t | [gregtech.waila.progress_tick: 3L, 10L]"), PlainProbeRenderer.render(lines));
        assertEquals(0xFF4CBB17, bar(lines).color());
        assertTrue(bar(lines).grouped());
    }

    @Test
    public void longerRecipesCountRoundedSeconds() {
        List<ProbeLine> lines = produce(false, false, 150, 400);

        assertEquals(List.of("bar 8/20 s | [gregtech.waila.progress_sec: 8L, 20L]"), PlainProbeRenderer.render(lines));
        assertEquals(0xFFBB1C28, bar(lines).color());
    }

    @Test
    public void computationShowsOnlyComputationUnits() {
        List<ProbeLine> lines = produce(true, true, 5, 30);

        assertEquals(List.of("bar 5/30 CWU | [gregtech.waila.progress_computation: 5L, 30L]"),
                PlainProbeRenderer.render(lines));
        assertEquals(0xFF00D4CE, bar(lines).color());
        assertFalse(bar(lines).grouped());
    }

    @Test
    public void noDurationShowsNoBar() {
        assertEquals(List.of(), produce(false, true, 0, 0));
    }
}

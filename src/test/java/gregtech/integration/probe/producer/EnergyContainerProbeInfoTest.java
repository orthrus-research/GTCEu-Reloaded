package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeProgress;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnergyContainerProbeInfoTest {

    @Test
    public void storedEnergyIsABarWithTextForProbesWithoutBars() {
        List<ProbeLine> lines = new ArrayList<>();
        EnergyContainerProbeInfo.addLines(250, 800, true, lines);

        assertEquals(List.of("bar 250/800 EU | [gregtech.waila.energy_stored: 250, 800]"),
                PlainProbeRenderer.render(lines));
        ProbeProgress bar = Objects.requireNonNull(lines.get(0).getProgress());
        assertEquals(0xFFEEE600, bar.color());
        assertTrue(bar.compact());
    }

    @Test
    public void containersWithoutCapacityShowNothing() {
        List<ProbeLine> lines = new ArrayList<>();
        EnergyContainerProbeInfo.addLines(0, 0, true, lines);

        assertEquals(List.of(), lines);
    }
}

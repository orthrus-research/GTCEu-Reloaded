package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrimitivePumpProbeInfoTest {

    @Test
    public void showsProductionPerSecond() {
        List<ProbeLine> lines = new ArrayList<>();
        PrimitivePumpProbeInfo.addLines(350, lines);

        assertEquals(List.of("<info>[gregtech.top.primitive_pump_production] &b350&r L/s"),
                PlainProbeRenderer.render(lines));
    }
}

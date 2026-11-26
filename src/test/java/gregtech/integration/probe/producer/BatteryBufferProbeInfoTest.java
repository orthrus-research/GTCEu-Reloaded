package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatteryBufferProbeInfoTest {

    @Test
    public void showsAverageInputAndOutput() {
        List<ProbeLine> lines = new ArrayList<>();
        BatteryBufferProbeInfo.addLines(120, 30, lines);

        assertEquals(List.of("&a[gregtech.battery_buffer.average_input.top: &f120 EU/t]",
                "&c[gregtech.battery_buffer.average_output.top: &f30 EU/t]"), PlainProbeRenderer.render(lines));
    }
}

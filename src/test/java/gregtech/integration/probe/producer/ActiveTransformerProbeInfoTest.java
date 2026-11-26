package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActiveTransformerProbeInfoTest {

    @Test
    public void showsAverageThroughput() {
        List<ProbeLine> lines = new ArrayList<>();
        ActiveTransformerProbeInfo.addLines(2048, lines);

        assertEquals(List.of("&b[gregtech.multiblock.active_transformer.average_io: &f2048 EU/t]"),
                PlainProbeRenderer.render(lines));
    }
}

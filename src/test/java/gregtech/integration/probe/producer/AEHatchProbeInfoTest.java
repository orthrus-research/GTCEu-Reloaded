package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AEHatchProbeInfoTest {

    private static List<String> lines(boolean online) {
        List<ProbeLine> lines = new ArrayList<>();
        AEHatchProbeInfo.addLines(online, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void showsTheNetworkStatus() {
        assertEquals(List.of("[gregtech.gui.me_network.online]"), lines(true));
        assertEquals(List.of("[gregtech.gui.me_network.offline]"), lines(false));
    }
}

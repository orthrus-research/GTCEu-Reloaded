package gregtech.integration.probe.producer;

import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DiodeProbeInfoTest {

    private static List<String> lines(EnumFacing side) {
        List<ProbeLine> lines = new ArrayList<>();
        int outputSides = 1 << EnumFacing.EAST.getIndex();
        DiodeProbeInfo.addLines(4, 2, ProbeLine.ALL_SIDES & ~outputSides, outputSides, lines);
        return PlainProbeRenderer.render(lines, side);
    }

    @Test
    public void inputSidesShowTheInputAmperage() {
        assertEquals(List.of("<info>&6[gregtech.top.transform_input] &r4 A"), lines(EnumFacing.WEST));
    }

    @Test
    public void theOutputSideShowsTheOutputAmperage() {
        assertEquals(List.of("<info>&9[gregtech.top.transform_output] &r2 A"), lines(EnumFacing.EAST));
    }
}

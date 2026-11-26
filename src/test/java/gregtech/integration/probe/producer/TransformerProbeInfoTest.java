package gregtech.integration.probe.producer;

import gregtech.api.GTValues;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransformerProbeInfoTest {

    private static final String LV = GTValues.VNF[GTValues.LV].replace('§', '&');
    private static final String MV = GTValues.VNF[GTValues.MV].replace('§', '&');

    private static List<ProbeLine> stepDown() {
        List<ProbeLine> lines = new ArrayList<>();
        int inputSides = 1 << EnumFacing.NORTH.getIndex();
        ProbeText input = TransformerProbeInfo.energy(GTValues.V[GTValues.MV], 1);
        ProbeText output = TransformerProbeInfo.energy(GTValues.V[GTValues.LV], 4);
        TransformerProbeInfo.addLines(false, input, output, inputSides, ProbeLine.ALL_SIDES & ~inputSides, lines);
        return lines;
    }

    @Test
    public void theInputSideShowsTheInput() {
        assertEquals(List.of(
                "<info>&a[gregtech.top.transform_down] &r" + MV + "&a (1A) -> " + LV + "&a (4A)",
                "<info>&6[gregtech.top.transform_input] &r" + MV + "&a (1A)"),
                PlainProbeRenderer.render(stepDown(), EnumFacing.NORTH));
    }

    @Test
    public void otherSidesShowTheOutput() {
        assertEquals(List.of(
                "<info>&a[gregtech.top.transform_down] &r" + MV + "&a (1A) -> " + LV + "&a (4A)",
                "<info>&9[gregtech.top.transform_output] &r" + LV + "&a (4A)"),
                PlainProbeRenderer.render(stepDown(), EnumFacing.SOUTH));
    }

    @Test
    public void steppingUpIsRed() {
        List<ProbeLine> lines = new ArrayList<>();
        TransformerProbeInfo.addLines(true, ProbeText.literal("in"), ProbeText.literal("out"), 0, 0, lines);

        assertEquals(List.of("<info>&c[gregtech.top.transform_up] &rin -> out"), PlainProbeRenderer.render(lines));
    }
}

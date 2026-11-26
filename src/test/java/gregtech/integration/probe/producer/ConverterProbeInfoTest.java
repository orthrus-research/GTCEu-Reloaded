package gregtech.integration.probe.producer;

import gregtech.api.GTValues;
import gregtech.api.capability.FeCompat;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConverterProbeInfoTest {

    private static final String ULV = GTValues.VNF[GTValues.ULV].replace('§', '&');

    private static List<String> lines(boolean feToEu, EnumFacing side) {
        List<ProbeLine> lines = new ArrayList<>();
        ConverterProbeInfo.addLines(feToEu, GTValues.V[GTValues.ULV], 1, EnumFacing.NORTH, lines);
        return PlainProbeRenderer.render(lines, side);
    }

    private static String fe(boolean feToEu) {
        return "&c" + FeCompat.toFe(GTValues.V[GTValues.ULV], FeCompat.ratio(feToEu)) + " FE";
    }

    @Test
    public void feToEuGivesEuAtTheFront() {
        assertEquals(List.of("<info>[gregtech.top.convert_fe]",
                "<info>[gregtech.top.transform_output] " + ULV + "&a (1A)"), lines(true, EnumFacing.NORTH));
        assertEquals(List.of("<info>[gregtech.top.convert_fe]",
                "<info>[gregtech.top.transform_input] " + fe(true)), lines(true, EnumFacing.UP));
    }

    @Test
    public void euToFeGivesFeAtTheFront() {
        assertEquals(List.of("<info>[gregtech.top.convert_eu]",
                "<info>[gregtech.top.transform_output] " + fe(false)), lines(false, EnumFacing.NORTH));
        assertEquals(List.of("<info>[gregtech.top.convert_eu]",
                "<info>[gregtech.top.transform_input] " + ULV + "&a (1A)"), lines(false, EnumFacing.SOUTH));
    }
}

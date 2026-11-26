package gregtech.integration.probe.producer;

import gregtech.Bootstrap;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaintenanceProbeInfoTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    private static List<String> lines(boolean hasProblems, int problems, boolean sneaking) {
        List<ProbeLine> lines = new ArrayList<>();
        MaintenanceProbeInfo.addLines(hasProblems, problems, sneaking, problem -> new ItemStack(Items.STICK), lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void noProblemsIsOk() {
        assertEquals(List.of("<ok>[gregtech.top.maintenance_fixed]"), lines(false, 0b111111, true));
    }

    @Test
    public void problemsShowAsOneRedLineUntilSneaking() {
        assertEquals(List.of("&c[gregtech.top.maintenance_broken]"), lines(true, 0b101101, false));
    }

    @Test
    public void sneakingListsEachProblemWithItsTool() {
        // bits 1 and 4 are clear: loose screws and burned wires
        assertEquals(List.of("{minecraft:stick} &c[gregtech.top.maintenance.screwdriver]",
                "{minecraft:stick} &c[gregtech.top.maintenance.wire_cutter]"), lines(true, 0b101101, true));
    }
}

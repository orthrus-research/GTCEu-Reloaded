package gregtech.integration.probe.producer;

import gregtech.Bootstrap;
import gregtech.api.unification.material.Materials;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SteamBoilerProbeInfoTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    private static List<String> lines(int steamOutput, boolean burning, boolean hasWater, int temperature) {
        List<ProbeLine> lines = new ArrayList<>();
        SteamBoilerProbeInfo.addLines(steamOutput, burning, hasWater, temperature, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void aBurningBoilerShowsItsSteam() {
        assertEquals(List.of("<info>[gregtech.top.energy_production] &b12<info> L/t [" +
                Materials.Steam.getUnlocalizedName() + "]"), lines(120, true, true, 100));
    }

    @Test
    public void aBoilerWithoutFuelCoolsDown() {
        assertEquals(List.of("<info>[gregtech.top.energy_production] &b6<info> L/t [" +
                Materials.Steam.getUnlocalizedName() + "]", "<info>&c[gregtech.top.steam_cooling_down]"),
                lines(60, false, true, 100));
    }

    @Test
    public void aColdBoilerHeatsUpAndWarnsWithoutWater() {
        assertEquals(List.of("<info>&c[gregtech.top.steam_heating_up] 40%", "<warning>[gregtech.top.steam_no_water]"),
                lines(0, true, false, 40));
    }

    @Test
    public void anIdleBoilerShowsNothing() {
        assertEquals(List.of(), lines(0, false, false, 0));
    }
}

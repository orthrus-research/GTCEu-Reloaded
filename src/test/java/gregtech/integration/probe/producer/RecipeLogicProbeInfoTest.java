package gregtech.integration.probe.producer;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecipeLogicProbeInfoTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    private static List<String> failure(RecipeFailure failure) {
        List<ProbeLine> lines = new ArrayList<>();
        RecipeLogicProbeInfo.addFailure(failure, lines);
        return PlainProbeRenderer.render(lines);
    }

    private static List<String> energy(long eut, boolean consumes, boolean steam) {
        List<ProbeLine> lines = new ArrayList<>();
        RecipeLogicProbeInfo.addEnergy(eut, consumes, steam, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void failuresAreWarnings() {
        assertEquals(List.of("<warning>[gregtech.recipe.failure.no_recipe]"),
                failure(RecipeFailure.of(RecipeFailureReason.NO_RECIPE)));
    }

    @Test
    public void failureAmountsFollowTheShortKey() {
        assertEquals(List.of("<warning>[gregtech.recipe.failure.temperature.short] " +
                "([gregtech.recipe.failure.required]: 450, [gregtech.recipe.failure.available]: 360)"),
                failure(RecipeFailure.of(RecipeFailureReason.TEMPERATURE, 450, 360)));
    }

    @ParameterizedTest
    @EnumSource(value = RecipeFailureReason.class, names = { "NONE", "DISABLED", "UNFORMED", "MUFFLER_OBSTRUCTED" })
    public void statesOtherLinesShowAreLeftOut(RecipeFailureReason reason) {
        assertEquals(List.of(), failure(RecipeFailure.of(reason)));
    }

    @Test
    public void consumptionShowsVoltageTier() {
        String tier = GTValues.VOCNF[GTUtility.getOCTierByVoltage(32)].replace('§', '&');

        assertEquals(List.of("<info>[gregtech.top.energy_consumption] &c32<info> EU/t&a (" + tier + "&a)"),
                energy(32, true, false));
    }

    @Test
    public void steamMachinesShowSteam() {
        assertEquals(List.of("<info>[gregtech.top.energy_production] &b12<info> L/t [" +
                Materials.Steam.getUnlocalizedName() + "]"), energy(12, false, true));
    }

    @Test
    public void noEnergyShowsNothing() {
        assertEquals(List.of(), energy(0, true, false));
    }
}

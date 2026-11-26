package gregtech.integration.probe.producer;

import gregtech.api.unification.material.Materials;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.steam.boiler.SteamBoiler;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;

import java.util.List;

/** A small steam boiler's steam output, heating up or cooling down, and missing water. */
public class SteamBoilerProbeInfo implements ProbeInfoProducer {

    @Override
    public void addInfo(ProbeContext context, List<ProbeLine> lines) {
        if (context.metaTileEntity() instanceof SteamBoiler boiler) {
            addLines(boiler.getTotalSteamOutput(), boiler.isBurning(), boiler.hasWater(),
                    boiler.getCurrentTemperature(), lines);
        }
    }

    static void addLines(int steamOutput, boolean burning, boolean hasWater, int temperature,
                         List<ProbeLine> lines) {
        // a boiler that neither makes steam nor has fuel has nothing to report
        if (steamOutput <= 0 && !burning) return;

        if (steamOutput > 0 && hasWater) {
            lines.add(ProbeLine.text(ProbeText.builder()
                    .style(ProbeStyle.INFO)
                    .translation("gregtech.top.energy_production")
                    .literal(" ")
                    .format(TextFormatting.AQUA)
                    .literal(TextFormattingUtil.formatNumbers(steamOutput / 10))
                    .style(ProbeStyle.INFO)
                    .literal(" L/t ")
                    .translation(Materials.Steam.getUnlocalizedName())
                    .build()));
        }
        if (!burning) {
            lines.add(ProbeLine.text(red("gregtech.top.steam_cooling_down").build()));
        }
        // the temperature counts the percent heated until the boiler makes steam
        if (steamOutput <= 0 && temperature > 0) {
            lines.add(ProbeLine.text(red("gregtech.top.steam_heating_up")
                    .literal(" " + TextFormattingUtil.formatNumbers(temperature) + "%")
                    .build()));
        }
        if (!hasWater) {
            lines.add(ProbeLine.text(ProbeText.builder()
                    .style(ProbeStyle.WARNING)
                    .translation("gregtech.top.steam_no_water")
                    .build()));
        }
    }

    private static ProbeText.Builder red(String key) {
        return ProbeText.builder().style(ProbeStyle.INFO).format(TextFormatting.RED).translation(key);
    }
}

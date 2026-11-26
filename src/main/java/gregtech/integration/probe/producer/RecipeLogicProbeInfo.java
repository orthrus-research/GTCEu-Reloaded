package gregtech.integration.probe.producer;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.capability.impl.PrimitiveRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.multiblock.RecipeMapSteamMultiblockController;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.multi.MetaTileEntityLargeBoiler;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;

/** Why a machine's recipe is not running, and the energy or steam a running recipe uses or produces. */
public class RecipeLogicProbeInfo extends CapabilityProbeInfoProducer<AbstractRecipeLogic> {

    @Override
    protected Capability<AbstractRecipeLogic> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_RECIPE_LOGIC;
    }

    @Override
    protected void addInfo(AbstractRecipeLogic capability, ProbeContext context, List<ProbeLine> lines) {
        addFailure(capability.getRecipeFailure(), lines);
        // primitive machines are meant to look unpowered
        if (!capability.isWorking() || capability instanceof PrimitiveRecipeLogic) return;

        MetaTileEntity metaTileEntity = context.metaTileEntity();
        boolean steam = metaTileEntity instanceof SteamMetaTileEntity ||
                metaTileEntity instanceof MetaTileEntityLargeBoiler ||
                metaTileEntity instanceof RecipeMapSteamMultiblockController;
        addEnergy(capability.getInfoProviderEUt(), capability.consumesEnergy(), steam, lines);
    }

    static void addFailure(RecipeFailure failure, List<ProbeLine> lines) {
        RecipeFailureReason reason = failure.getReason();
        // the controllable and multiblock lines already show these states
        if (reason == RecipeFailureReason.NONE || reason == RecipeFailureReason.DISABLED ||
                reason == RecipeFailureReason.UNFORMED || reason == RecipeFailureReason.MUFFLER_OBSTRUCTED) {
            return;
        }

        ProbeText.Builder text = ProbeText.builder().style(ProbeStyle.WARNING);
        if (reason.hasConditions()) {
            text.recipeFailure(failure);
        } else if (reason.hasAmounts()) {
            // the short key leaves out the arguments, so The One Probe's client can translate it
            text.translation(reason.getTranslationKey() + ".short")
                    .literal(" (")
                    .translation("gregtech.recipe.failure.required")
                    .literal(": " + TextFormattingUtil.formatNumbers(failure.getRequired()) + ", ")
                    .translation("gregtech.recipe.failure.available")
                    .literal(": " + TextFormattingUtil.formatNumbers(failure.getAvailable()) + ")");
        } else {
            text.translation(reason.getTranslationKey());
        }
        lines.add(ProbeLine.text(text.build()));
    }

    static void addEnergy(long eut, boolean consumes, boolean steam, List<ProbeLine> lines) {
        if (eut == 0) return;

        ProbeText.Builder text = ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation(consumes ? "gregtech.top.energy_consumption" : "gregtech.top.energy_production")
                .literal(" ");
        if (steam) {
            text.format(TextFormatting.AQUA)
                    .literal(TextFormattingUtil.formatNumbers(eut))
                    .style(ProbeStyle.INFO)
                    .literal(" L/t ")
                    .translation(Materials.Steam.getUnlocalizedName());
        } else {
            text.format(TextFormatting.RED)
                    .literal(TextFormattingUtil.formatNumbers(eut))
                    .style(ProbeStyle.INFO)
                    .literal(" EU/t")
                    .format(TextFormatting.GREEN)
                    .literal(" (" + GTValues.VOCNF[GTUtility.getOCTierByVoltage(eut)])
                    .format(TextFormatting.GREEN)
                    .literal(")");
        }
        lines.add(ProbeLine.text(text.build()));
    }
}

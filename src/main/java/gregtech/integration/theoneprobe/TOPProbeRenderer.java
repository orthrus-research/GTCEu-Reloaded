package gregtech.integration.theoneprobe;

import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.util.LocalizationUtils;
import gregtech.api.util.TextFormattingUtil;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeProgress;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;
import gregtech.integration.probe.ProbeTextRenderer;

import net.minecraft.item.ItemStack;

import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.TextStyleClass;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;

/**
 * Draws shared probe lines into The One Probe's info on the server. Keys without arguments become markers that The
 * One Probe's client translates; keys with arguments, which it cannot translate, are translated here.
 */
public final class TOPProbeRenderer implements ProbeTextRenderer {

    /** Values from this size up are shortened while the player is not sneaking. */
    private static final long COMPACT_FROM = 10_000;
    private static final int BORDER_COLOR = 0xFF555555;

    private final boolean sneaking;

    public TOPProbeRenderer(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public void render(ProbeLine line, IProbeInfo probeInfo) {
        ProbeProgress progress = line.getProgress();
        if (progress != null) {
            renderProgress(progress, probeInfo);
            return;
        }

        ProbeText text = line.getText();
        ItemStack icon = line.getIcon();
        if (icon.isEmpty()) {
            if (text != null) probeInfo.text(text.render(this));
            return;
        }

        IProbeInfo horizontal = probeInfo
                .horizontal(probeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER));
        horizontal.item(icon);
        if (text == null) {
            horizontal.itemLabel(icon);
        } else {
            horizontal.text(text.render(this));
        }
    }

    private void renderProgress(ProbeProgress progress, IProbeInfo probeInfo) {
        NumberFormat full = progress.grouped() ? NumberFormat.COMMAS : NumberFormat.FULL;
        boolean compact = progress.compact() && !sneaking;
        NumberFormat current = compact && progress.current() >= COMPACT_FROM ? NumberFormat.COMPACT : full;
        String max = compact && progress.max() >= COMPACT_FROM ?
                ElementProgress.format(progress.max(), NumberFormat.COMPACT, progress.unit()) :
                ElementProgress.format(progress.max(), full, " " + progress.unit());
        probeInfo.progress(progress.current(), progress.max(), probeInfo.defaultProgressStyle()
                .numberFormat(current)
                .suffix(" / " + max)
                .filledColor(progress.color())
                .alternateFilledColor(progress.color())
                .borderColor(BORDER_COLOR));
    }

    @Override
    public String translate(String key, Object... arguments) {
        if (arguments.length == 0) return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
        return LocalizationUtils.format(key, arguments);
    }

    @Override
    public String style(ProbeStyle style) {
        return switch (style) {
            case INFO -> TextStyleClass.INFO.toString();
            case INFO_IMPORTANT -> TextStyleClass.INFOIMP.toString();
            case LABEL -> TextStyleClass.LABEL.toString();
            case OK -> TextStyleClass.OK.toString();
            case WARNING -> TextStyleClass.WARNING.toString();
        };
    }

    @Override
    public String quantity(long value, String unit) {
        if (sneaking || value < COMPACT_FROM) return TextFormattingUtil.formatNumbers(value) + " " + unit;
        return ElementProgress.format(value, NumberFormat.COMPACT, unit);
    }

    /** Translated whole here: the failure bounds its condition details by length, which markers would distort. */
    @Override
    public String recipeFailure(RecipeFailure failure) {
        return failure.format(LocalizationUtils::format);
    }
}

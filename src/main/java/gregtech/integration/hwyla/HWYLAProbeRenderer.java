package gregtech.integration.hwyla;

import gregtech.api.util.TextFormattingUtil;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeProgress;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;
import gregtech.integration.probe.ProbeTextRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Turns shared probe lines into HWYLA tooltip lines on the client, translated in the player's language. Styles take
 * The One Probe's default colours; progress bars show their fallback text.
 */
@SideOnly(Side.CLIENT)
public final class HWYLAProbeRenderer implements ProbeTextRenderer {

    public static final HWYLAProbeRenderer INSTANCE = new HWYLAProbeRenderer();

    private HWYLAProbeRenderer() {}

    public String render(ProbeLine line) {
        ProbeProgress progress = line.getProgress();
        if (progress != null) return progress.fallback().render(this);

        ProbeText text = line.getText();
        ItemStack icon = line.getIcon();
        if (icon.isEmpty()) return text == null ? "" : text.render(this);
        return HWYLAModule.wailaStackWithName(icon, text == null ? icon.getDisplayName() : text.render(this));
    }

    @Override
    public String translate(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }

    @Override
    public String style(ProbeStyle style) {
        return switch (style) {
            case INFO -> TextFormatting.RESET.toString();
            case INFO_IMPORTANT -> TextFormatting.BLUE.toString();
            case LABEL -> TextFormatting.GRAY.toString();
            case OK -> TextFormatting.GREEN.toString();
            case WARNING -> TextFormatting.YELLOW.toString();
        };
    }

    @Override
    public String quantity(long value, String unit) {
        return TextFormattingUtil.formatNumbers(value) + " " + unit;
    }
}

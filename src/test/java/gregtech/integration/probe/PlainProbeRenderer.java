package gregtech.integration.probe;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Renders probe lines plainly for assertions: a key in brackets with its arguments, a style in angle brackets, a
 * formatting code with {@code &} for {@code §}, a bar as {@code bar current/max unit} before its fallback text, and an
 * icon as its item's registry name in braces.
 */
public final class PlainProbeRenderer implements ProbeTextRenderer {

    public static final PlainProbeRenderer INSTANCE = new PlainProbeRenderer();

    private PlainProbeRenderer() {}

    /** The lines shown while looking at a side, or only the lines for every side when it is null. */
    public static List<String> render(List<ProbeLine> lines, @Nullable EnumFacing side) {
        List<String> rendered = new ArrayList<>();
        for (ProbeLine line : lines) {
            if (line.isVisibleFrom(side)) rendered.add(render(line));
        }
        return rendered;
    }

    public static List<String> render(List<ProbeLine> lines) {
        return render(lines, null);
    }

    public static String render(ProbeLine line) {
        String text;
        ProbeProgress progress = line.getProgress();
        ProbeText lineText = line.getText();
        if (progress != null) {
            text = "bar " + progress.current() + "/" + progress.max() + " " + progress.unit() + " | " +
                    progress.fallback().render(INSTANCE);
        } else {
            text = lineText == null ? "(name)" : lineText.render(INSTANCE);
        }

        ItemStack icon = line.getIcon();
        if (!icon.isEmpty()) text = "{" + icon.getItem().getRegistryName() + "} " + text;
        return text.replace('§', '&');
    }

    @Override
    public String translate(String key, Object... arguments) {
        if (arguments.length == 0) return "[" + key + "]";
        StringJoiner joined = new StringJoiner(", ", "[" + key + ": ", "]");
        for (Object argument : arguments) {
            joined.add(argument instanceof Long ? argument + "L" : String.valueOf(argument));
        }
        return joined.toString();
    }

    @Override
    public String style(ProbeStyle style) {
        return "<" + style.name().toLowerCase(Locale.ROOT) + ">";
    }

    @Override
    public String quantity(long value, String unit) {
        return value + " " + unit;
    }
}

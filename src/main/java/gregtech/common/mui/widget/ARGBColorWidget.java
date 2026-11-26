package gregtech.common.mui.widget;

import com.cleanroommc.modularui.api.value.IIntValue;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Edits an ARGB colour as four two-digit hex fields, alpha first, followed by a swatch of the colour. An empty field
 * reads as zero.
 */
public class ARGBColorWidget extends ParentWidget<ARGBColorWidget> {

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]*");
    private static final int FIELD_WIDTH = 20;
    private static final int FIELD_SPACING = 22;

    public ARGBColorWidget(IIntValue<?> color, int height) {
        size(4 * FIELD_SPACING + 12, height);
        for (int channel = 0; channel < 4; channel++) {
            int shift = 24 - 8 * channel;
            child(new TextFieldWidget()
                    .pos(channel * FIELD_SPACING, 0)
                    .size(FIELD_WIDTH, height)
                    .setMaxLength(2)
                    .setPattern(HEX)
                    .setTextAlignment(Alignment.Center)
                    .value(new StringValue.Dynamic(
                            () -> Integer.toHexString(color.getIntValue() >> shift & 0xFF).toUpperCase(Locale.ROOT),
                            text -> color.setIntValue(withChannel(color.getIntValue(), shift, text)))));
        }
        Rectangle swatch = new Rectangle();
        child(new Widget<>()
                .pos(4 * FIELD_SPACING, 0)
                .size(12, height)
                .background(new DynamicDrawable(() -> swatch.setColor(color.getIntValue()))));
    }

    /** Replaces the eight bits of {@code color} at {@code shift} with the hex digits {@code text}. */
    public static int withChannel(int color, int shift, String text) {
        int value = 0;
        if (text != null && !text.isEmpty() && text.length() <= 2 && HEX.matcher(text).matches()) {
            value = Integer.parseInt(text, 16);
        }
        return (color & ~(0xFF << shift)) | (value << shift);
    }
}

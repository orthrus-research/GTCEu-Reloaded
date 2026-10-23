package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.widgets.SimpleTextWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Supplier;

/** One bounded text row: transport is capped and the client clips by actual glyph width. */
public final class PatternBufferTextWidget extends SimpleTextWidget {

    public PatternBufferTextWidget(int x, int y, Supplier<String> text) {
        super(x, y, "", 0x404040, () -> bounded(text.get()));
        setCenter(false);
        setScale(0.8f);
    }

    private static String bounded(String text) {
        return text.length() > 100 ? text.substring(0, 97) + "..." : text;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        String original = lastText;
        String resolved = I18n.hasKey(original) ? I18n.format(original) : original;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int pixels = 202; // 162 screen pixels at 0.8 scale.
        this.lastText = font.getStringWidth(resolved) <= pixels ? resolved :
                font.trimStringToWidth(resolved, pixels - font.getStringWidth("...")) + "...";
        try {
            super.drawInBackground(mouseX, mouseY, partialTicks, context);
        } finally {
            this.lastText = original;
        }
    }
}

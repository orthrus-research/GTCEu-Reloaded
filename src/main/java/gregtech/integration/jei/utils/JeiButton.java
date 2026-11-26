package gregtech.integration.jei.utils;

import gregtech.client.utils.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import com.cleanroommc.modularui.drawable.UITexture;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JeiButton {

    private Layer[] layers = {};
    private final float x;
    private final float y;
    private final int width;
    private final int height;
    private ClickAction clickAction;
    private BooleanSupplier activeSupplier = () -> true;
    private Consumer<List<String>> tooltipBuilder = null;

    public JeiButton(float x, float y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Draws the layers across the button, each over the ones before it. */
    public JeiButton setTextures(Layer... layers) {
        this.layers = layers;
        return this;
    }

    /** Draws the texture stretched across the button. */
    public JeiButton setTextures(UITexture texture) {
        return setTextures(texture(texture));
    }

    public JeiButton setClickAction(ClickAction clickAction) {
        this.clickAction = clickAction;
        return this;
    }

    public JeiButton setActiveSupplier(BooleanSupplier activeSupplier) {
        this.activeSupplier = activeSupplier;
        return this;
    }

    public JeiButton setTooltipBuilder(Consumer<List<String>> tooltipBuilder) {
        this.tooltipBuilder = tooltipBuilder;
        return this;
    }

    public void buildTooltip(List<String> lines) {
        if (tooltipBuilder != null) {
            tooltipBuilder.accept(lines);
        }
    }

    public boolean isHovering(int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height &&
                activeSupplier.getAsBoolean();
    }

    public void render(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        if (!activeSupplier.getAsBoolean())
            return;
        for (Layer layer : layers) {
            layer.draw(x, y, width, height);
        }
    }

    public ClickAction getClickAction() {
        return clickAction;
    }

    /** A layer that draws the texture stretched across the button. */
    public static Layer texture(UITexture texture) {
        return (x, y, width, height) -> RenderUtil.renderTextureArea(texture, x, y, width, height, 0);
    }

    /**
     * A layer that draws the text's first line, wrapped 4 pixels narrower than the button and ending in {@code ..}
     * when more follows, centred on the button in the colour without a shadow.
     */
    public static Layer label(Supplier<String> text, int color) {
        return (x, y, width, height) -> {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            List<String> lines = font.listFormattedStringToWidth(text.get(), width - 4);
            String line = lines.isEmpty() ? "" : lines.get(0) + (lines.size() > 1 ? ".." : "");
            font.drawString(line, x + (width - font.getStringWidth(line)) / 2f, y + (height - font.FONT_HEIGHT) / 2f,
                    color, false);
            GlStateManager.color(1, 1, 1, 1);
        };
    }

    /** What a button draws across its area. */
    @FunctionalInterface
    public interface Layer {

        void draw(float x, float y, int width, int height);
    }

    @FunctionalInterface
    public interface ClickAction {

        boolean click(Minecraft minecraft, int mouseX, int mouseY, int mouseButton);
    }
}

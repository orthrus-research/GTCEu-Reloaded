package gregtech.common.mui.widget;

import gregtech.api.GTValues;
import gregtech.api.mui.GTGuiTextures;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import com.cleanroommc.modularui.api.value.IIntValue;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The wiring task a plain maintenance hatch shows on April Fools' day: drag each wire on the left to the wire of its
 * colour on the right. Each connection is added to a bit mask the server holds; the colours and their order are drawn
 * by the client alone.
 */
public class FixWiringTaskWidget extends Widget<FixWiringTaskWidget> implements Interactable {

    /** The mask once all four wires are connected. */
    public static final int ALL_CONNECTED = 0b1111;
    private static final float IMAGE_SIZE = 504f;
    private final int[] colors = new int[4];
    private final int[] order = new int[4];
    private final IIntValue<?> connections;
    private BooleanSupplier canInteract = () -> true;
    private int dragged = -1;

    /**
     * @param connections the bit mask of connected wires, bit {@code i} for the left wire {@code i}
     */
    public FixWiringTaskWidget(IIntValue<?> connections) {
        this.connections = connections;
        for (int i = 0; i < 4; i++) {
            this.colors[i] = (GTValues.RNG.nextInt() & 0x00ffffff) | 0xff000000;
        }
        List<Integer> shuffled = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            this.order[i] = shuffled.get(i);
        }
    }

    public FixWiringTaskWidget canInteract(BooleanSupplier canInteract) {
        this.canInteract = canInteract;
        return this;
    }

    private boolean isConnected(int wire) {
        return (connections.getIntValue() & (1 << wire)) != 0;
    }

    private float wireY(int wire) {
        return getArea().height / IMAGE_SIZE * ((wire + 1) * 104 - 10);
    }

    /** @return the wire on that side under the mouse, or -1 */
    private int wireUnderMouse(boolean left) {
        if (!canInteract.getAsBoolean()) return -1;
        float xScale = getArea().width / IMAGE_SIZE;
        float yScale = getArea().height / IMAGE_SIZE;
        int mouseX = getContext().getMouseX();
        int mouseY = getContext().getMouseY();
        float boxWidth = xScale * 38 * 1.5f;
        float boxX = left ? 0 : getArea().width - boxWidth;
        for (int i = 0; i < 4; i++) {
            float boxY = wireY(i) - yScale * 16;
            if (mouseX >= boxX && mouseX < boxX + boxWidth && mouseY >= boxY && mouseY < boxY + yScale * 64) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int width = getArea().width;
        int height = getArea().height;
        float xScale = width / IMAGE_SIZE;
        float yScale = height / IMAGE_SIZE;
        GTGuiTextures.WIRING_BACKGROUND.draw(0, 0, width, height);
        for (int i = 0; i < 4; i++) {
            if (!isConnected(i)) continue;
            for (int j = 0; j < order.length; j++) {
                if (order[j] == i) {
                    drawLine(xScale * 25, wireY(i) + yScale * 12, width - xScale * 25, wireY(j) + yScale * 12,
                            colors[i]);
                    break;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            float y = wireY(i);
            if (dragged == i) {
                drawLine(xScale * 25, y + yScale * 12, context.getMouseX(), context.getMouseY(), colors[i]);
            }
            GTGuiTextures.WIRING_WIRE_LEFT.draw(xScale * 25, y - yScale * 5, xScale * 38, yScale * 32);
            Color.setGlColor(colors[i]);
            GTGuiTextures.WIRING_BASE_LEFT.draw(0, y, xScale * 50, yScale * 32);
            GlStateManager.color(1, 1, 1, 1);

            GTGuiTextures.WIRING_WIRE_RIGHT.draw(width - xScale * (25 + 38), y - yScale * 5, xScale * 38,
                    yScale * 32);
            Color.setGlColor(colors[order[i]]);
            GTGuiTextures.WIRING_BASE_RIGHT.draw(width - xScale * 50, y, xScale * 50, yScale * 32);
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static void drawLine(float x1, float y1, float x2, float y2, int color) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(5);
        Color.setGlColor(color);
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        buffer.pos(x1, y1, 0).endVertex();
        buffer.pos(x2, y2, 0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        int over = wireUnderMouse(true);
        if (over != -1 && !isConnected(over)) {
            this.dragged = over;
        }
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (dragged != -1) {
            int over = wireUnderMouse(false);
            if (over != -1 && order[over] == dragged) {
                connections.setIntValue(connections.getIntValue() | (1 << dragged));
            }
        }
        this.dragged = -1;
        return true;
    }
}

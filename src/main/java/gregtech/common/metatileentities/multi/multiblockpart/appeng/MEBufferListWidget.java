package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.mui.GTGuiTextures;

import appeng.api.storage.data.IAEStack;
import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The entries an ME output part waits to send, a row each with the resource and its amount; the mouse wheel scrolls
 * when there are more entries than rows.
 */
final class MEBufferListWidget<T extends IAEStack<T>> extends Widget<MEBufferListWidget<T>>
                              implements Interactable {

    private static final int ROW_HEIGHT = 18;
    private static final int AMOUNT_WIDTH = 140;
    private final MEStackType<T, ?> stackType;
    private final Supplier<List<T>> entries;
    private final int rows;
    private final TextRenderer textRenderer = new TextRenderer();
    private int firstRow;
    /** The row under the mouse when the list was last drawn, which the tooltip names. */
    private int hoveredRow = -1;

    MEBufferListWidget(MEStackType<T, ?> stackType, Supplier<List<T>> entries, int rows) {
        this.stackType = stackType;
        this.entries = entries;
        this.rows = rows;
        size(ROW_HEIGHT + AMOUNT_WIDTH, rows * ROW_HEIGHT);
        tooltipAutoUpdate(true);
        tooltipBuilder(this::buildTooltip);
    }

    @Override
    public void onInit() {
        this.textRenderer.setShadow(true);
        this.textRenderer.setColor(Color.WHITE.main);
    }

    @Override
    protected WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
        return theme.getItemSlotTheme();
    }

    private int lastFirstRow() {
        return Math.max(0, entries.get().size() - rows);
    }

    @Nullable
    private T entryAt(int row) {
        List<T> shown = entries.get();
        int index = Math.min(firstRow, lastFirstRow()) + row;
        return row >= 0 && row < rows && index < shown.size() ? shown.get(index) : null;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int mouseX = context.getMouseX();
        int mouseY = context.getMouseY();
        this.hoveredRow = isHovering() && mouseX >= 0 && mouseX < getArea().width && mouseY >= 0 ?
                mouseY / ROW_HEIGHT : -1;
        for (int row = 0; row < rows; row++) {
            int y = row * ROW_HEIGHT;
            stackType.slotBackground().draw(context, 0, y, ROW_HEIGHT, ROW_HEIGHT, widgetTheme.getTheme());
            GTGuiTextures.NUMBER_BACKGROUND.draw(context, ROW_HEIGHT, y, AMOUNT_WIDTH, ROW_HEIGHT,
                    widgetTheme.getTheme());
            T entry = entryAt(row);
            if (entry != null) {
                stackType.draw(entry, 1, y + 1);
                this.textRenderer.setPos(ROW_HEIGHT + 2, y + 6);
                this.textRenderer.draw(String.format("x%,d", entry.getStackSize()));
            }
            if (row == hoveredRow) {
                GuiDraw.drawRect(1, y + 1, 16, 16, 0x80FFFFFF);
            }
        }
    }

    private void buildTooltip(RichTooltip tooltip) {
        T entry = entryAt(hoveredRow);
        if (entry != null) stackType.addTooltip(tooltip, entry, true);
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        int last = lastFirstRow();
        if (last == 0) return false;
        int row = Math.min(firstRow, last) + (scrollDirection.isUp() ? -1 : 1);
        this.firstRow = Math.max(0, Math.min(last, row));
        return true;
    }
}

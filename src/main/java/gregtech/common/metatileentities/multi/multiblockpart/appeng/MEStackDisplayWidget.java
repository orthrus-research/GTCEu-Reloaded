package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.util.TextFormattingUtil;
import gregtech.client.utils.RenderUtil;

import appeng.api.storage.data.IAEStack;
import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;

import java.util.function.Supplier;

/** Shows one ME stack with its amount and tooltip, as a config slot's stock or an output part's waiting entry. */
final class MEStackDisplayWidget<T extends IAEStack<T>> extends Widget<MEStackDisplayWidget<T>> {

    private final MEStackType<T, ?> stackType;
    private final Supplier<T> stack;
    private final TextRenderer textRenderer = new TextRenderer();

    MEStackDisplayWidget(MEStackType<T, ?> stackType, Supplier<T> stack, IDrawable background) {
        this.stackType = stackType;
        this.stack = stack;
        size(18);
        background(background);
        tooltipAutoUpdate(true);
        tooltipBuilder(tooltip -> {
            T shown = stack.get();
            if (shown != null) stackType.addTooltip(tooltip, shown, true);
        });
    }

    @Override
    public void onInit() {
        this.textRenderer.setShadow(true);
        this.textRenderer.setScale(0.5f);
        this.textRenderer.setColor(Color.WHITE.main);
    }

    @Override
    protected WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
        return theme.getItemSlotTheme();
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        T shown = stack.get();
        if (shown != null) {
            stackType.draw(shown, 1, 1);
            this.textRenderer.setAlignment(Alignment.CenterRight, getArea().width - 1f);
            this.textRenderer.setPos(0, 12);
            this.textRenderer.draw(TextFormattingUtil.formatLongToCompactString(shown.getStackSize(), 4) +
                    stackType.unit());
        }
        RenderUtil.handleSlotOverlay(this, widgetTheme);
    }
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.utils.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One config slot of an ME input part: click with a stack to configure it, right-click to clear it, scroll to change
 * its amount (by doubling with Ctrl), or drop a recipe viewer ingredient on it. A stack draws with its amount unless
 * the part is stocking, where amounts mean nothing; while auto-pull manages the slot it is dark and takes no edits.
 */
final class MEConfigSlotWidget<T extends IAEStack<T>, N> extends Widget<MEConfigSlotWidget<T, N>>
                              implements Interactable, RecipeViewerGhostIngredientSlot<N> {

    private final MEConfigSyncHandler<T, N> config;
    private final int index;
    private final TextRenderer textRenderer = new TextRenderer();

    MEConfigSlotWidget(MEConfigSyncHandler<T, N> config, int index) {
        this.config = config;
        this.index = index;
        size(18);
        tooltipAutoUpdate(true);
        tooltipBuilder(this::buildTooltip);
    }

    @Override
    public void onInit() {
        this.textRenderer.setShadow(true);
        this.textRenderer.setScale(0.5f);
        this.textRenderer.setColor(Color.WHITE.main);
        getContext().getRecipeViewerSettings().addGhostIngredientSlot(this);
    }

    @Override
    protected WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
        return theme.getItemSlotTheme();
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        for (var texture : config.getStackType().configBackground(config.isAutoPull())) {
            texture.draw(context, 0, 0, 18, 18, widgetTheme.getTheme());
        }
        if (config.getSelected() == index) {
            GTGuiTextures.SELECT_BOX.draw(context, 0, 0, 18, 18, widgetTheme.getTheme());
        }
        T stack = config.getConfig(index);
        if (stack != null) {
            config.getStackType().draw(stack, 1, 1);
            if (!config.isStocking()) {
                String amount = TextFormattingUtil.formatLongToCompactString(stack.getStackSize(), 4) +
                        config.getStackType().unit();
                this.textRenderer.setAlignment(Alignment.CenterRight, getArea().width - 1f);
                this.textRenderer.setPos(0, 12);
                this.textRenderer.draw(amount);
            }
        }
        RenderUtil.handleJEIGhostSlotOverlay(this, widgetTheme);
    }

    private void buildTooltip(RichTooltip tooltip) {
        T stack = config.getConfig(index);
        if (stack != null) {
            config.getStackType().addTooltip(tooltip, stack, false);
            return;
        }
        tooltip.addLine(IKey.lang("gregtech.gui.config_slot"));
        if (config.isAutoPull()) {
            tooltip.addLine(IKey.lang("gregtech.gui.config_slot.auto_pull_managed"));
            return;
        }
        if (config.isStocking()) {
            tooltip.addLine(IKey.lang("gregtech.gui.config_slot.set_only"));
        } else {
            tooltip.addLine(IKey.lang("gregtech.gui.config_slot.set"));
            tooltip.addLine(IKey.lang("gregtech.gui.config_slot.scroll"));
        }
        tooltip.addLine(IKey.lang("gregtech.gui.config_slot.remove"));
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (config.isAutoPull()) return Result.IGNORE;
        if (mouseButton == 1) {
            config.clear(index);
        } else if (mouseButton == 0) {
            // what the cursor holds configures the slot; the click also selects it for its amount
            ItemStack cursor = Minecraft.getMinecraft().player.inventory.getItemStack();
            if (config.getStackType().fromCursor(cursor) != null) config.setFromCursor(index);
            config.select(index);
        }
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        T stack = config.getConfig(index);
        if (config.isStocking() || config.isAutoPull() || stack == null) return false;
        long size = stack.getStackSize();
        long newSize;
        if (Interactable.hasControlDown()) {
            newSize = scrollDirection.isUp() ? size * 2 : size / 2;
        } else {
            newSize = scrollDirection.isUp() ? size + 1 : size - 1;
        }
        if (newSize > 0 && newSize <= Integer.MAX_VALUE) {
            config.setAmount(index, newSize);
            return true;
        }
        return false;
    }

    @Override
    public void setGhostIngredient(@NotNull N ingredient) {
        if (!config.isAutoPull()) config.setFromIngredient(index, ingredient);
    }

    @Override
    public @Nullable N castGhostIngredientIfValid(@NotNull Object ingredient) {
        return config.isAutoPull() ? null : config.getStackType().fromIngredient(ingredient);
    }
}

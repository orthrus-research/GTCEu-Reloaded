package gregtech.common.mui.widget.monitor;

import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityCentralMonitor;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * One cell of a central monitor's screen grid: the screen's frame colour while it is active, a larger view of it
 * while hovered, and a click opens it. The cell names its screen by column and row only; the server looks it up.
 */
public class MonitorScreenGridWidget extends Widget<MonitorScreenGridWidget> implements Interactable {

    public static final int SIZE = 20;

    private final MetaTileEntityCentralMonitor controller;
    private final int column;
    private final int row;
    private final BiConsumer<Integer, Integer> onClick;

    public MonitorScreenGridWidget(MetaTileEntityCentralMonitor controller, int column, int row,
                                   BiConsumer<Integer, Integer> onClick) {
        this.controller = controller;
        this.column = column;
        this.row = row;
        this.onClick = onClick;
        size(SIZE);
    }

    private MetaTileEntityMonitorScreen getMonitorScreen() {
        MetaTileEntityMonitorScreen[][] screens = controller == null ? null : controller.screens;
        if (screens == null || column >= screens.length || row >= screens[column].length) return null;
        return screens[column][row];
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        MetaTileEntityMonitorScreen screen = getMonitorScreen();
        int color = screen != null && screen.isActive() ? screen.frameColor : 0xFF000000;
        GuiDraw.drawRect(1, 1, SIZE - 3, SIZE - 3, color);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void drawForeground(ModularGuiContext context) {
        MetaTileEntityMonitorScreen screen = getMonitorScreen();
        if (!isHovering() || screen == null || !screen.isActive()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(SIZE / 2F, SIZE / 2F, 100);
        GlStateManager.scale(SIZE, SIZE, 1);
        RenderUtil.renderRect(-0.5f, -0.5f, screen.scale, screen.scale, 0, 0xFF000000);
        screen.renderScreen(0, null);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        Interactable.playButtonClickSound();
        onClick.accept(column, row);
        return Result.SUCCESS;
    }
}

package gregtech.common.mui.widget.monitor;

import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/** Shows what a monitor screen displays, framed in grey, at the size of the widget. */
public class MonitorScreenPreviewWidget extends Widget<MonitorScreenPreviewWidget> {

    private final MetaTileEntityMonitorScreen screen;

    public MonitorScreenPreviewWidget(MetaTileEntityMonitorScreen screen, int size) {
        this.screen = screen;
        size(size + 4);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int width = getArea().width;
        int height = getArea().height;
        RenderUtil.renderRect(0, 0, width, height, 0, 0xFF7B7A7C);
        RenderUtil.renderRect(2, 2, width - 4, height - 4, 0, 0xFF000000);

        if (screen != null && screen.isActive()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(2 + 0.5 * (width - 4), 2 + 0.5 * (height - 4), 0);
            GlStateManager.scale(width, width, 1.0f / width);
            GlStateManager.scale(1 / screen.scale, 1 / screen.scale, 1 / screen.scale);
            GlStateManager.translate(-(screen.scale - 1) * 0.5, -(screen.scale - 1) * 0.5, 0);
            screen.renderScreen(0, null);
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}

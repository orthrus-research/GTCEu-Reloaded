package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.client.utils.RenderUtil;
import gregtech.common.mui.widget.ARGBColorWidget;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import java.util.Arrays;

public class TextPluginBehavior extends MonitorPluginBaseBehavior {

    /** The longest line the server keeps; clients read lines no longer than this. */
    public static final int MAX_LINE_LENGTH = 100;

    public String[] texts;
    public int[] colors;

    /** Sets a line; a client sends {@code line} and {@code text}, so a line out of range or no text is ignored. */
    public void setText(int line, String text, int color) {
        if (line < 0 || line >= texts.length || text == null) return;
        text = limit(text);
        if (texts[line].equals(text) && colors[line] == color) return;
        this.texts[line] = text;
        this.colors[line] = color;
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, packetBuffer -> {
            packetBuffer.writeInt(texts.length);
            for (int i = 0; i < texts.length; i++) {
                packetBuffer.writeString(texts[i]);
                packetBuffer.writeInt(colors[i]);
            }
        });
        markAsDirty();
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            texts = new String[buf.readInt()];
            colors = new int[texts.length];
            for (int i = 0; i < texts.length; i++) {
                texts[i] = buf.readString(MAX_LINE_LENGTH);
                colors[i] = buf.readInt();
            }
        }
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        TextPluginBehavior plugin = new TextPluginBehavior();
        plugin.texts = new String[16];
        plugin.colors = new int[16];
        return plugin;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        for (int i = 0; i < texts.length; i++) {
            data.setString("t" + i, texts[i]);
        }
        data.setIntArray("color", colors);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        for (int i = 0; i < texts.length; i++) {
            texts[i] = data.hasKey("t" + i) ? limit(data.getString("t" + i)) : "";
        }
        // keep one colour per line whatever length was saved
        int[] saved = data.hasKey("color") ? data.getIntArray("color") : new int[0];
        Arrays.fill(colors, 0XFFFFFFFF);
        System.arraycopy(saved, 0, colors, 0, Math.min(saved.length, colors.length));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        for (int i = 0; i < texts.length; i++) {
            RenderUtil.renderText(-0.5f, -0.4625f + i / 16f, 0.002f, 1 / 128f, colors[i], texts[i], false);
        }
    }

    @Override
    public boolean hasUI() {
        return true;
    }

    @Override
    public int getConfigWidth() {
        return 260;
    }

    @Override
    public int getConfigHeight() {
        return 210;
    }

    @Override
    public void buildConfigUI(ParentWidget<?> parent, PanelSyncManager syncManager) {
        for (int i = 0; i < texts.length; i++) {
            int line = i;
            StringSyncValue text = new StringSyncValue(() -> this.texts[line],
                    value -> setText(line, value, this.colors[line]));
            IntSyncValue color = new IntSyncValue(() -> this.colors[line],
                    value -> setText(line, this.texts[line], value));
            syncManager.syncValue("text", line, text);
            syncManager.syncValue("color", line, color);
            parent.child(new TextFieldWidget()
                    .pos(25, 25 + line * 10)
                    .size(100, 10)
                    .setMaxLength(MAX_LINE_LENGTH)
                    .value(text))
                    .child(new ARGBColorWidget(color, 10).pos(135, 25 + line * 10));
        }
    }

    private static String limit(String text) {
        return text.length() > MAX_LINE_LENGTH ? text.substring(0, MAX_LINE_LENGTH) : text;
    }
}

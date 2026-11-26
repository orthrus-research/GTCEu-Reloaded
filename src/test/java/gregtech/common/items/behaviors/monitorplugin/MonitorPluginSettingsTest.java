package gregtech.common.items.behaviors.monitorplugin;

import gregtech.BootstrapExtension;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;
import gregtech.common.mui.widget.ARGBColorWidget;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings a client sends to a monitor plugin reach these setters on the server, which keep them in range. */
@ExtendWith(BootstrapExtension.class)
public class MonitorPluginSettingsTest {

    private static TextPluginBehavior textPlugin() {
        TextPluginBehavior plugin = (TextPluginBehavior) new TextPluginBehavior().createPlugin();
        plugin.readFromNBT(new NBTTagCompound());
        return plugin;
    }

    private static OnlinePicPluginBehavior picturePlugin() {
        OnlinePicPluginBehavior plugin = (OnlinePicPluginBehavior) new OnlinePicPluginBehavior().createPlugin();
        plugin.readFromNBT(new NBTTagCompound());
        return plugin;
    }

    @Test
    public void Test_Text_Lines_Are_Cut_To_What_Clients_Read() {
        TextPluginBehavior plugin = textPlugin();
        plugin.setText(3, "x".repeat(500), 0xFF00FF00);

        assertEquals(TextPluginBehavior.MAX_LINE_LENGTH, plugin.texts[3].length());
        assertEquals(0xFF00FF00, plugin.colors[3]);
    }

    @Test
    public void Test_Text_Lines_Out_Of_Range_Or_Missing_Are_Ignored() {
        TextPluginBehavior plugin = textPlugin();
        plugin.setText(-1, "a", 1);
        plugin.setText(plugin.texts.length, "a", 1);
        plugin.setText(0, null, 1);

        for (int i = 0; i < plugin.texts.length; i++) {
            assertEquals("", plugin.texts[i]);
        }
        assertEquals(0xFFFFFFFF, plugin.colors[0]);
    }

    @Test
    public void Test_Saved_Text_Longer_Than_Clients_Read_Is_Cut() {
        TextPluginBehavior plugin = (TextPluginBehavior) new TextPluginBehavior().createPlugin();
        NBTTagCompound data = new NBTTagCompound();
        data.setString("t0", "y".repeat(300));
        plugin.readFromNBT(data);

        assertEquals(TextPluginBehavior.MAX_LINE_LENGTH, plugin.texts[0].length());
    }

    @Test
    public void Test_Only_Web_Addresses_Are_Allowed() {
        assertTrue(OnlinePicPluginBehavior.isAllowedUrl(""));
        assertTrue(OnlinePicPluginBehavior.isAllowedUrl("https://example.com/picture.png"));
        assertTrue(OnlinePicPluginBehavior.isAllowedUrl("HTTP://example.com:8080/a.gif?size=2"));

        assertFalse(OnlinePicPluginBehavior.isAllowedUrl(null));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("file:///etc/passwd"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("ftp://example.com/a.png"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("javascript:alert(1)"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("https:///no-host.png"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("example.com/a.png"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("https://example.com/a b.png"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("https://example.com/\n.png"));
        assertFalse(OnlinePicPluginBehavior.isAllowedUrl("https://example.com/" + "a".repeat(200)));
    }

    @Test
    public void Test_A_Refused_Address_Changes_Nothing() {
        OnlinePicPluginBehavior plugin = picturePlugin();
        plugin.setConfig("file:///etc/passwd", 90, 0.5f, 0.5f, true, false);

        assertEquals("", plugin.url);
        assertEquals(0, plugin.rotation);
        assertFalse(plugin.flippedX);
    }

    @Test
    public void Test_Picture_Settings_Stay_In_Range() {
        OnlinePicPluginBehavior plugin = picturePlugin();
        plugin.setConfig("https://example.com/a.png", 720, 5, -3, false, true);

        assertEquals("https://example.com/a.png", plugin.url);
        assertEquals(180, plugin.rotation);
        assertEquals(1, plugin.scaleX);
        assertEquals(0, plugin.scaleY);
        assertTrue(plugin.flippedY);

        plugin.setConfig(plugin.url, Float.NaN, 0.5f, 0.5f, false, false);
        assertEquals(180, plugin.rotation);
        assertEquals(1, plugin.scaleX);
    }

    @Test
    public void Test_A_Saved_Address_Longer_Than_Clients_Read_Is_Dropped() {
        OnlinePicPluginBehavior plugin = (OnlinePicPluginBehavior) new OnlinePicPluginBehavior().createPlugin();
        NBTTagCompound data = new NBTTagCompound();
        data.setString("url", "https://example.com/" + "a".repeat(300));
        plugin.readFromNBT(data);

        assertEquals("", plugin.url);
    }

    @Test
    public void Test_Snapped_Settings_Stay_On_Their_Steps() {
        assertEquals(0.5f, MonitorPluginBaseBehavior.snap(0.52, 0.3, 2, 0.1), 1e-6);
        assertEquals(2f, MonitorPluginBaseBehavior.snap(9, 0.3, 2, 0.1), 1e-6);
        assertEquals(-180f, MonitorPluginBaseBehavior.snap(-1e9, -180, 180, 1), 1e-6);
        assertTrue(Float.isNaN(MonitorPluginBaseBehavior.snap(Double.NaN, 0, 1, 0.05)));
        assertTrue(Float.isNaN(MonitorPluginBaseBehavior.snap(Double.POSITIVE_INFINITY, 0, 1, 0.05)));
    }

    @Test
    public void Test_Screen_Scale_Is_Checked() {
        assertTrue(MetaTileEntityMonitorScreen.isValidScale(1));
        assertTrue(MetaTileEntityMonitorScreen.isValidScale(8));
        assertFalse(MetaTileEntityMonitorScreen.isValidScale(0.9f));
        assertFalse(MetaTileEntityMonitorScreen.isValidScale(8.1f));
        assertFalse(MetaTileEntityMonitorScreen.isValidScale(Float.NaN));
        assertFalse(MetaTileEntityMonitorScreen.isValidScale(Float.POSITIVE_INFINITY));
    }

    @Test
    public void Test_A_Colour_Channel_Edit_Keeps_The_Others() {
        int color = 0x80112233;
        assertEquals(0x80114433, ARGBColorWidget.withChannel(color, 8, "44"));
        assertEquals(0xFF112233, ARGBColorWidget.withChannel(color, 24, "ff"));
        assertEquals(0x00112233, ARGBColorWidget.withChannel(color, 24, ""));
        assertEquals(0x80002233, ARGBColorWidget.withChannel(color, 16, "zz"));
    }
}

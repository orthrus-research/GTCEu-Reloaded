package gregtech.common.items.behaviors.monitorplugin;

import gregtech.BootstrapExtension;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.common.items.MetaItems;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The GUI proxy plugin no longer mirrors machine screens, but its item and its saved part stay. */
@ExtendWith(BootstrapExtension.class)
public class FakeGuiPluginTest {

    @Test
    public void Test_The_Plugin_Item_Keeps_Its_Behaviour() {
        assertInstanceOf(FakeGuiPluginBehavior.class,
                MonitorPluginBaseBehavior.getBehavior(MetaItems.PLUGIN_FAKE_GUI.getStackForm()));
    }

    @Test
    public void Test_A_Saved_Part_Loads_And_Saves_Again() {
        FakeGuiPluginBehavior plugin = (FakeGuiPluginBehavior) new FakeGuiPluginBehavior().createPlugin();
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("part", 3);
        plugin.readFromNBT(saved);

        NBTTagCompound written = new NBTTagCompound();
        plugin.writeToNBT(written);
        assertEquals(3, written.getInteger("part"));
    }

    @Test
    public void Test_A_Negative_Part_Is_Ignored() {
        FakeGuiPluginBehavior plugin = (FakeGuiPluginBehavior) new FakeGuiPluginBehavior().createPlugin();
        plugin.readFromNBT(new NBTTagCompound());
        plugin.setConfig(-2);

        NBTTagCompound written = new NBTTagCompound();
        plugin.writeToNBT(written);
        assertEquals(0, written.getInteger("part"));
    }

    @Test
    public void Test_The_Notice_Has_An_English_Text() throws IOException {
        try (InputStream lang = FakeGuiPluginTest.class.getResourceAsStream("/assets/gregtech/lang/en_us.lang")) {
            assertNotNull(lang);
            String text = new String(lang.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.lines().anyMatch(line -> line.startsWith(FakeGuiPluginBehavior.NOTICE + "=")),
                    FakeGuiPluginBehavior.NOTICE + " is missing from en_us.lang");
        }
    }
}

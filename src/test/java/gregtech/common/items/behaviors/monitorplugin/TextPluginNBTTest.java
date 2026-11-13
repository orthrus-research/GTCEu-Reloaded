package gregtech.common.items.behaviors.monitorplugin;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TextPluginNBTTest {

    @Test
    public void Test_Short_Colour_Array_Keeps_One_Colour_Per_Line() {
        TextPluginBehavior plugin = (TextPluginBehavior) new TextPluginBehavior().createPlugin();
        NBTTagCompound data = new NBTTagCompound();
        data.setIntArray("color", new int[] { 0xFF112233, 0xFF445566 });
        plugin.readFromNBT(data);

        assertThat(plugin.colors.length, is(plugin.texts.length));
        assertThat(plugin.colors[1], is(0xFF445566));
        assertThat(plugin.colors[15], is(0xFFFFFFFF));
    }

    @Test
    public void Test_Long_Colour_Array_Is_Truncated() {
        TextPluginBehavior plugin = (TextPluginBehavior) new TextPluginBehavior().createPlugin();
        NBTTagCompound data = new NBTTagCompound();
        data.setIntArray("color", new int[20]);
        plugin.readFromNBT(data);

        assertThat(plugin.colors.length, is(plugin.texts.length));
    }
}

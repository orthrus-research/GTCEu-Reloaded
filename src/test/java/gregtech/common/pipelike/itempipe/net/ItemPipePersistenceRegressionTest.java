package gregtech.common.pipelike.itempipe.net;

import gregtech.api.unification.material.properties.ItemPipeProperties;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemPipePersistenceRegressionTest {

    @Test
    void writesCanonicalAndDowngradePriorityKeys() {
        Codec codec = new Codec();
        NBTTagCompound tag = new NBTTagCompound();
        codec.writeNodeData(new ItemPipeProperties(17, 0.5f), tag);
        assertEquals(17, tag.getInteger("Resistance"));
        assertEquals(17, tag.getInteger("Range"));
        assertEquals(new ItemPipeProperties(17, 0.5f), codec.readNodeData(tag));
    }

    @Test
    void canonicalIntegerPriorityWinsAndLegacyRangeRemainsReadable() {
        Codec codec = new Codec();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Range", 19);
        tag.setFloat("Rate", 0.5f);
        assertEquals(19, codec.readNodeData(tag).getPriority());
        tag.setInteger("Resistance", 23);
        assertEquals(23, codec.readNodeData(tag).getPriority());
        tag.setString("Resistance", "malformed");
        assertEquals(19, codec.readNodeData(tag).getPriority());
    }

    @Test
    void malformedRatesAreExplicitlyRejected() {
        Codec codec = new Codec();
        for (float invalid : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.5f }) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Resistance", 1);
            tag.setFloat("Rate", invalid);
            assertThrows(IllegalArgumentException.class, () -> codec.readNodeData(tag));
        }
    }

    @Test
    void missingOptionalValuesKeepLegacyDefaultsAndNegativePriority() {
        Codec codec = new Codec();
        NBTTagCompound tag = new NBTTagCompound();
        assertEquals(new ItemPipeProperties(0, 0), codec.readNodeData(tag));
        tag.setInteger("Resistance", -3);
        assertEquals(-3, codec.readNodeData(tag).getPriority());
    }

    @Test
    void malformedTypedPriorityAndRateAreExplicitFixtures() {
        Codec codec = new Codec();
        NBTTagCompound priority = new NBTTagCompound();
        priority.setString("Resistance", "17");
        assertThrows(IllegalArgumentException.class, () -> codec.readNodeData(priority));
        NBTTagCompound rate = new NBTTagCompound();
        rate.setString("Rate", "0.5");
        assertThrows(IllegalArgumentException.class, () -> codec.readNodeData(rate));
    }

    private static final class Codec extends ItemPipeNet {

        private Codec() {
            super(new WorldItemPipeNet("item_codec_regression"));
        }
    }
}

package gregtech.api.worldgen;

import gregtech.api.worldgen.generator.GTWorldGenCapability;

import net.minecraft.nbt.NBTTagCompound;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

public class WorldGenCapabilityNBTTest {

    @Test
    public void savedBottomHeightIsRestored() {
        GTWorldGenCapability original = new GTWorldGenCapability();
        original.setMaxHeight(100, 60);

        GTWorldGenCapability loaded = new GTWorldGenCapability();
        loaded.readFromNBT(original.writeToNBT());

        MatcherAssert.assertThat(loaded.getMaxHeight(), is(100));
        MatcherAssert.assertThat(loaded.getMaxBottomHeight(), is(60));
    }

    @Test
    public void missingBottomHeightDefaultsToTheMaximum() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("H", 80);
        GTWorldGenCapability loaded = new GTWorldGenCapability();
        loaded.readFromNBT(tag);
        MatcherAssert.assertThat(loaded.getMaxBottomHeight(), is(80));
    }
}

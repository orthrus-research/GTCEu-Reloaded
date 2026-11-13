package gregtech.api.recipes.ingredients;

import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.recipes.ingredients.nbtmatch.NBTTagType;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagLongArray;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NBTMatcherValueTest {

    private static NBTTagCompound tag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("bytes", new byte[] { 1, 2, 3 });
        tag.setIntArray("ints", new int[] { 4, 5 });
        tag.setTag("longs", new NBTTagLongArray(new long[] { 6L, 7L }));
        return tag;
    }

    @Test
    public void equalArraysMatch() {
        for (NBTMatcher matcher : new NBTMatcher[] { NBTMatcher.EQUAL_TO, NBTMatcher.RECURSIVE_EQUAL_TO }) {
            Assertions.assertTrue(matcher.evaluate(tag(),
                    NBTCondition.create(NBTTagType.BYTE_ARRAY, "bytes", new byte[] { 1, 2, 3 })));
            Assertions.assertTrue(matcher.evaluate(tag(),
                    NBTCondition.create(NBTTagType.INT_ARRAY, "ints", new int[] { 4, 5 })));
            Assertions.assertTrue(matcher.evaluate(tag(),
                    NBTCondition.create(NBTTagType.LONG_ARRAY, "longs", new long[] { 6L, 7L })));
        }
    }

    @Test
    public void differentArraysDoNotMatch() {
        for (NBTMatcher matcher : new NBTMatcher[] { NBTMatcher.EQUAL_TO, NBTMatcher.RECURSIVE_EQUAL_TO }) {
            Assertions.assertFalse(matcher.evaluate(tag(),
                    NBTCondition.create(NBTTagType.BYTE_ARRAY, "bytes", new byte[] { 1, 2 })));
            Assertions.assertFalse(matcher.evaluate(tag(),
                    NBTCondition.create(NBTTagType.INT_ARRAY, "ints", new int[] { 4, 6 })));
            Assertions.assertFalse(matcher.evaluate(tag(),
                    NBTCondition.create(NBTTagType.LONG_ARRAY, "longs", new long[] { 6L })));
        }
    }

    @Test
    public void numericConditionsAcceptAnyNumberType() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("count", 5);
        tag.setShort("small", (short) 2);
        Assertions.assertTrue(NBTMatcher.EQUAL_TO.evaluate(tag, NBTCondition.create(NBTTagType.INT, "count", 5)));
        Assertions.assertTrue(NBTMatcher.EQUAL_TO.evaluate(tag, NBTCondition.create(NBTTagType.INT, "count", 5L)));
        Assertions.assertTrue(
                NBTMatcher.EQUAL_TO.evaluate(tag, NBTCondition.create(NBTTagType.SHORT, "small", (short) 2)));
        Assertions.assertFalse(NBTMatcher.EQUAL_TO.evaluate(tag, NBTCondition.create(NBTTagType.INT, "count", 6)));
    }
}

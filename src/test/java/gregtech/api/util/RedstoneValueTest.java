package gregtech.api.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RedstoneValueTest {

    @Test
    public void Test_Ordinary_Ratio() {
        assertThat(RedstoneUtil.computeRedstoneValue(500, 1000, false), is(7));
        assertThat(RedstoneUtil.computeRedstoneValue(1000, 1000, false), is(15));
        assertThat(RedstoneUtil.computeRedstoneValue(0, 1000, true), is(15));
    }

    @Test
    public void Test_Full_Long_Capacity_Reads_Full() {
        assertThat(RedstoneUtil.computeRedstoneValue(Long.MAX_VALUE, Long.MAX_VALUE, false), is(15));
    }

    @Test
    public void Test_Half_Long_Capacity_Reads_Seven() {
        assertThat(RedstoneUtil.computeRedstoneValue(Long.MAX_VALUE / 2, Long.MAX_VALUE, false), is(7));
    }
}

package gregtech.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class NearestLesserTest {

    @Test
    public void belowTheFirstEntryReturnsMinusOne() {
        assertEquals(-1, GTUtility.nearestLesserOrEqual(new long[] { 10, 20 }, 5));
        assertEquals(-1, GTUtility.nearestLesser(new long[] { 10, 20 }, 10));
        assertEquals(-1, GTUtility.nearestLesserOrEqual(new long[] { 1, 2, 3, 4, 5, 6 }, 0));
        assertEquals(-1, GTUtility.nearestLesserOrEqual(new long[0], 7));
    }

    @Test
    public void findsTheNearestEntry() {
        long[] array = { 10, 20, 30, 40, 50, 60 };
        assertEquals(0, GTUtility.nearestLesserOrEqual(array, 10));
        assertEquals(2, GTUtility.nearestLesserOrEqual(array, 39));
        assertEquals(5, GTUtility.nearestLesserOrEqual(array, 1000));
        assertEquals(1, GTUtility.nearestLesser(array, 30));
        assertEquals(5, GTUtility.nearestLesser(array, 61));
    }

    @Test
    public void compactFormatRejectsPrecisionBelowThree() {
        assertThrows(IllegalArgumentException.class, () -> TextFormattingUtil.formatLongToCompactString(123456, 2));
    }
}

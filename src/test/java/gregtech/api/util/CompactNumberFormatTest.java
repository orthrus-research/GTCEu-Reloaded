package gregtech.api.util;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.hamcrest.CoreMatchers.is;

public class CompactNumberFormatTest {

    private static String compact(long value, int precision) {
        return TextFormattingUtil.formatBigIntToCompactString(BigInteger.valueOf(value), precision);
    }

    @Test
    public void belowThePrecisionThresholdPrintsTheWholeNumber() {
        MatcherAssert.assertThat(compact(0, 7), is("0"));
        MatcherAssert.assertThat(compact(9_999_999, 7), is("9999999"));
    }

    @Test
    public void fractionFollowsTheIntegerPart() {
        MatcherAssert.assertThat(compact(12_345_678, 7), is("12.3456M"));
        MatcherAssert.assertThat(compact(123_456_789, 7), is("123.4567M"));
        MatcherAssert.assertThat(compact(1_234_567_890, 7), is("1.2345G"));
        MatcherAssert.assertThat(compact(-12_345_678, 7), is("-12.3456M"));
    }

    @Test
    public void leadingZerosOfTheFractionAreKept() {
        MatcherAssert.assertThat(compact(10_050_000, 7), is("10.0500M"));
        MatcherAssert.assertThat(compact(1_000_000_123L, 7), is("1.0000G"));
    }

    @Test
    public void valuesAboveTheLongRangeKeepTheirSuffix() {
        BigInteger value = new BigInteger("123456789012345678901234");
        MatcherAssert.assertThat(TextFormattingUtil.formatBigIntToCompactString(value, 7), is("123.4567Z"));
    }
}

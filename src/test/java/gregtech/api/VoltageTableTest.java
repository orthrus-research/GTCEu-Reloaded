package gregtech.api;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class VoltageTableTest {

    @Test
    public void Test_VAOC_Is_Fifteen_Sixteenths_Of_VOC() {
        assertThat(GTValues.VAOC.length, is(GTValues.VOC.length));
        for (int i = 0; i < GTValues.VOC.length; i++) {
            long expected = BigInteger.valueOf(GTValues.VOC[i]).multiply(BigInteger.valueOf(15))
                    .divide(BigInteger.valueOf(16)).longValueExact();
            assertThat("tier " + i, GTValues.VAOC[i], is(expected));
        }
    }

    @Test
    public void Test_VAOC_Keeps_The_Correct_Low_Tiers() {
        assertThat(GTValues.VAOC[0], is(7L));
        assertThat(GTValues.VAOC[1], is(30L));
        assertThat(GTValues.VAOC[7], is(122880L));
    }
}

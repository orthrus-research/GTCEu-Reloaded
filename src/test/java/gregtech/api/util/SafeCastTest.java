package gregtech.api.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SafeCastTest {

    @Test
    public void Test_Safe_Cast_Clamps_Both_Ends() {
        assertThat(GTUtility.safeCastLongToInt(Long.MAX_VALUE), is(Integer.MAX_VALUE));
        assertThat(GTUtility.safeCastLongToInt(Long.MIN_VALUE), is(Integer.MIN_VALUE));
        assertThat(GTUtility.safeCastLongToInt(-5L), is(-5));
    }

    @Test
    public void Test_Generator_Tank_Size_At_Tier_Zero_Is_Not_Empty() {
        assertThat(GTUtility.steamGeneratorTankSizeFunction.apply(0), is(16000));
        assertThat(GTUtility.genericGeneratorTankSizeFunction.apply(0), is(4000));
        assertThat(GTUtility.genericGeneratorTankSizeFunction.apply(2), is(8000));
    }
}

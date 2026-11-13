package gregtech.api.capability;

import gregtech.api.GTValues;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeCompatTest {

    @Test
    void conversionSaturatesInsteadOfWrapping() {
        assertEquals(128, FeCompat.toFe(32, 4));
        assertEquals(Integer.MAX_VALUE, FeCompat.toFe(1L << 29, 4));
        assertEquals(Integer.MAX_VALUE, FeCompat.toFe(GTValues.V[GTValues.MAX], 4));
        assertEquals(Integer.MAX_VALUE, FeCompat.toFe(Long.MAX_VALUE, 4));
        assertEquals(Integer.MAX_VALUE, FeCompat.toFe(Integer.MAX_VALUE / 2 + 1, 2));
        assertEquals(0, FeCompat.toFe(-5, 4));
    }
}

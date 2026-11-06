package gregtech.common.mui.widget;

import gregtech.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GTTextFieldWidgetTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void evaluatesNumericExpressionsAndTheEmptyDefault() {
        var field = new GTTextFieldWidget().setDefaultNumber(42);
        assertEquals(14, field.parse("2 + 3 * 4"));
        assertEquals(0.25, field.parse("1 / 4"));
        assertEquals(42, field.parse(""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(", "TRUE", "\"text\"", "1 / 0" })
    void invalidAndNonNumericExpressionsKeepTheConfiguredFallback(String expression) {
        assertEquals(42, new GTTextFieldWidget().setDefaultNumber(42).parse(expression));
    }
}

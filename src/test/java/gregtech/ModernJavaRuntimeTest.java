package gregtech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModernJavaRuntimeTest {

    @Test
    void usesTheDeclaredModernRuntime() {
        assertEquals(GTInternalTags.JAVA_VERSION, Runtime.version().feature());
    }

    @Test
    void recordsAndPatternSwitchExecuteWithoutDesugaring() {
        assertTrue(Amount.class.isRecord());
        assertEquals(7, value(new Amount(7)));
        assertEquals(0, value("not an amount"));
        assertEquals(new Amount(7), new Amount(7));
    }

    private static int value(Object input) {
        return switch (input) {
            case Amount(int value) -> value;
            default -> 0;
        };
    }

    private record Amount(int value) {}
}

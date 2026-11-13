package gregtech.api.util;

import org.jetbrains.annotations.NotNull;

public final class SmallDigits {

    /** Subscript digits 0 to 9, contiguous from U+2080. */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final char[] SMALL_DOWN_DIGITS = "\u2080\u2081\u2082\u2083\u2084\u2085\u2086\u2087\u2088\u2089"
            .toCharArray();
    /** Superscript digits 0 to 9; 1, 2 and 3 sit in Latin-1, the others from U+2070. */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final char[] SMALL_UP_DIGITS = "\u2070\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079"
            .toCharArray();
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final int FORMATTING_SYMBOL = '\u00a7';

    private SmallDigits() {}

    public static @NotNull String toSmallUpNumbers(String string) {
        return convert(string, SMALL_UP_DIGITS);
    }

    public static @NotNull String toSmallDownNumbers(String string) {
        return convert(string, SMALL_DOWN_DIGITS);
    }

    private static @NotNull String convert(@NotNull String string, char @NotNull [] digits) {
        char[] chars = string.toCharArray();
        boolean hasPrefix = false;
        boolean hasFormat = false;
        for (int i = 0; i < chars.length; i++) {
            if (hasFormat) {
                // skip over the next character once the format flag is set
                hasFormat = false;
                continue;
            }

            char c = chars[i];
            if (c == '-') {
                hasPrefix = true;
                continue;
            }
            if (c == FORMATTING_SYMBOL) {
                hasFormat = true;
                continue;
            }

            if (c >= '0' && c <= '9') {
                if (!hasPrefix) {
                    chars[i] = digits[c - '0'];
                }
            } else {
                // reset prefix as soon as a non-digit is encountered
                hasPrefix = false;
            }
        }
        return new String(chars);
    }
}

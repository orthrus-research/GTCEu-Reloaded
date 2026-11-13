package gregtech.api.recipes;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Compiled once: {@link String#matches} compiles its pattern on every call, and these checks run per condition and
 * modifier, also while machines load from NBT and failure packets are decoded.
 */
public final class IdentifierPatterns {

    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern REASON_KEY = Pattern.compile("[a-zA-Z0-9_.-]+");

    private IdentifierPatterns() {}

    /** @return whether the text is a {@code namespace:path} resource id */
    public static boolean isResourceId(@NotNull String text) {
        return RESOURCE_ID.matcher(text).matches();
    }

    /** @return whether the text is a translation-key style reason key */
    public static boolean isReasonKey(@NotNull String text) {
        return REASON_KEY.matcher(text).matches();
    }
}

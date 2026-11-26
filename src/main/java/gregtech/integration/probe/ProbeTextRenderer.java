package gregtech.integration.probe;

import gregtech.api.recipes.logic.RecipeFailure;

/** Turns {@link ProbeText} into the formatted string one probe displays. */
public interface ProbeTextRenderer {

    /**
     * Translates a key with its arguments, each already rendered text or a number. A probe that translates on the
     * client may return a marker for the client to translate instead.
     */
    String translate(String key, Object... arguments);

    /** The formatting codes or markers that start a run of text in a style. */
    String style(ProbeStyle style);

    /** A value with its unit, such as {@code 12,345 EU/t}. */
    String quantity(long value, String unit);

    /** A recipe failure's explanation, its condition details included. */
    default String recipeFailure(RecipeFailure failure) {
        return failure.format(this::translate);
    }
}

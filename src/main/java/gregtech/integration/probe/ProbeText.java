package gregtech.integration.probe;

import gregtech.api.recipes.logic.RecipeFailure;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Probe text kept as runs of literal text, translations, styles and quantities, so that each probe translates and
 * colours it its own way: The One Probe on the server with markers its client translates, HWYLA on the client.
 * Instances are immutable.
 */
public final class ProbeText {

    public static final ProbeText EMPTY = new ProbeText(Collections.emptyList());

    private final List<ProbeTextPart> parts;

    private ProbeText(List<ProbeTextPart> parts) {
        this.parts = parts;
    }

    public static ProbeText literal(String text) {
        return new ProbeText(Collections.singletonList(ProbeTextPart.literal(text)));
    }

    /**
     * A translation whose arguments are {@link ProbeText}, strings (taken as literal text), ints or longs. A number
     * is passed to the translation as a {@link Long}, so it suits both {@code %s} and {@code %d}.
     */
    public static ProbeText translation(String key, Object... arguments) {
        List<Object> converted = new ArrayList<>(arguments.length);
        for (Object argument : arguments) {
            converted.add(convertArgument(argument));
        }
        return new ProbeText(Collections.singletonList(ProbeTextPart.translation(key, converted)));
    }

    private static Object convertArgument(Object argument) {
        if (argument instanceof ProbeText) return argument;
        if (argument instanceof String text) return literal(text);
        if (argument instanceof Integer || argument instanceof Long) return ((Number) argument).longValue();
        throw new IllegalArgumentException("Unsupported probe translation argument: " + argument);
    }

    /** Starts a run of text in a style; the run lasts until the next style or formatting code. */
    public static ProbeText style(ProbeStyle style) {
        return new ProbeText(Collections.singletonList(ProbeTextPart.style(style)));
    }

    /** A formatting code, which every probe draws the same. */
    public static ProbeText format(TextFormatting formatting) {
        return literal(formatting.toString());
    }

    /** A value with its unit; The One Probe shortens large values while the player is not sneaking. */
    public static ProbeText quantity(long value, String unit) {
        return new ProbeText(Collections.singletonList(ProbeTextPart.quantity(value, unit)));
    }

    /** A recipe failure's explanation, translated with its condition details as {@link RecipeFailure} formats. */
    public static ProbeText recipeFailure(RecipeFailure failure) {
        return new ProbeText(Collections.singletonList(ProbeTextPart.recipeFailure(failure)));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public String render(ProbeTextRenderer renderer) {
        StringBuilder text = new StringBuilder();
        for (ProbeTextPart part : parts) {
            text.append(part.render(renderer));
        }
        return text.toString();
    }

    public NBTTagList writeToNBT() {
        NBTTagList list = new NBTTagList();
        for (ProbeTextPart part : parts) {
            list.appendTag(part.writeToNBT());
        }
        return list;
    }

    public static ProbeText readFromNBT(NBTTagList list) {
        List<ProbeTextPart> parts = new ArrayList<>(list.tagCount());
        for (NBTBase element : list) {
            parts.add(ProbeTextPart.readFromNBT((NBTTagCompound) element));
        }
        return parts.isEmpty() ? EMPTY : new ProbeText(List.copyOf(parts));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ProbeText text && parts.equals(text.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return "ProbeText" + parts;
    }

    /** Joins runs of text in order. */
    public static final class Builder {

        private final List<ProbeTextPart> parts = new ArrayList<>();

        private Builder() {}

        public Builder append(ProbeText text) {
            this.parts.addAll(text.parts);
            return this;
        }

        public Builder literal(String text) {
            return append(ProbeText.literal(text));
        }

        public Builder translation(String key, Object... arguments) {
            return append(ProbeText.translation(key, arguments));
        }

        public Builder style(ProbeStyle style) {
            return append(ProbeText.style(style));
        }

        public Builder format(TextFormatting formatting) {
            return append(ProbeText.format(formatting));
        }

        public Builder quantity(long value, String unit) {
            return append(ProbeText.quantity(value, unit));
        }

        public Builder recipeFailure(RecipeFailure failure) {
            return append(ProbeText.recipeFailure(failure));
        }

        public ProbeText build() {
            return parts.isEmpty() ? EMPTY : new ProbeText(List.copyOf(parts));
        }
    }
}

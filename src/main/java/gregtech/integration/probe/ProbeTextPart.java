package gregtech.integration.probe;

import gregtech.api.recipes.logic.RecipeFailure;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One run of {@link ProbeText}; its kind says which of the other components it uses. */
record ProbeTextPart(Kind kind, String value, long number, List<Object> arguments, @Nullable ProbeStyle style,
                     @Nullable RecipeFailure failure) {

    enum Kind {
        LITERAL,
        TRANSLATION,
        STYLE,
        QUANTITY,
        RECIPE_FAILURE
    }

    static ProbeTextPart literal(String text) {
        return new ProbeTextPart(Kind.LITERAL, Objects.requireNonNull(text), 0, List.of(), null, null);
    }

    /** Arguments are {@link ProbeText} or {@link Long} values. */
    static ProbeTextPart translation(String key, List<Object> arguments) {
        return new ProbeTextPart(Kind.TRANSLATION, Objects.requireNonNull(key), 0, List.copyOf(arguments), null, null);
    }

    static ProbeTextPart style(ProbeStyle style) {
        return new ProbeTextPart(Kind.STYLE, "", 0, List.of(), Objects.requireNonNull(style), null);
    }

    static ProbeTextPart quantity(long value, String unit) {
        return new ProbeTextPart(Kind.QUANTITY, Objects.requireNonNull(unit), value, List.of(), null, null);
    }

    static ProbeTextPart recipeFailure(RecipeFailure failure) {
        return new ProbeTextPart(Kind.RECIPE_FAILURE, "", 0, List.of(), null, Objects.requireNonNull(failure));
    }

    String render(ProbeTextRenderer renderer) {
        return switch (kind) {
            case LITERAL -> value;
            case TRANSLATION -> renderer.translate(value, renderArguments(renderer));
            case STYLE -> renderer.style(Objects.requireNonNull(style));
            case QUANTITY -> renderer.quantity(number, value);
            case RECIPE_FAILURE -> renderer.recipeFailure(Objects.requireNonNull(failure));
        };
    }

    private Object[] renderArguments(ProbeTextRenderer renderer) {
        Object[] rendered = new Object[arguments.size()];
        for (int i = 0; i < rendered.length; i++) {
            Object argument = arguments.get(i);
            rendered[i] = argument instanceof ProbeText text ? text.render(renderer) : argument;
        }
        return rendered;
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Kind", (byte) kind.ordinal());
        switch (kind) {
            case LITERAL -> tag.setString("Value", value);
            case TRANSLATION -> {
                tag.setString("Value", value);
                NBTTagList list = new NBTTagList();
                for (Object argument : arguments) {
                    NBTTagCompound argumentTag = new NBTTagCompound();
                    if (argument instanceof ProbeText text) {
                        argumentTag.setTag("Text", text.writeToNBT());
                    } else {
                        argumentTag.setLong("Number", (Long) argument);
                    }
                    list.appendTag(argumentTag);
                }
                tag.setTag("Arguments", list);
            }
            case STYLE -> tag.setByte("Style", (byte) Objects.requireNonNull(style).ordinal());
            case QUANTITY -> {
                tag.setString("Value", value);
                tag.setLong("Number", number);
            }
            case RECIPE_FAILURE -> {
                NBTTagCompound failureTag = new NBTTagCompound();
                Objects.requireNonNull(failure).writeToNBT(failureTag);
                tag.setTag("Failure", failureTag);
            }
        }
        return tag;
    }

    static ProbeTextPart readFromNBT(NBTTagCompound tag) {
        Kind kind = Kind.values()[tag.getByte("Kind")];
        return switch (kind) {
            case LITERAL -> literal(tag.getString("Value"));
            case TRANSLATION -> {
                List<Object> arguments = new ArrayList<>();
                for (NBTBase element : tag.getTagList("Arguments", Constants.NBT.TAG_COMPOUND)) {
                    NBTTagCompound argumentTag = (NBTTagCompound) element;
                    if (argumentTag.hasKey("Text")) {
                        NBTTagList text = argumentTag.getTagList("Text", Constants.NBT.TAG_COMPOUND);
                        arguments.add(ProbeText.readFromNBT(text));
                    } else {
                        arguments.add(argumentTag.getLong("Number"));
                    }
                }
                yield translation(tag.getString("Value"), arguments);
            }
            case STYLE -> style(ProbeStyle.values()[tag.getByte("Style")]);
            case QUANTITY -> quantity(tag.getLong("Number"), tag.getString("Value"));
            case RECIPE_FAILURE -> recipeFailure(RecipeFailure.readFromNBT(tag.getCompoundTag("Failure")));
        };
    }
}

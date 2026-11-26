package gregtech.api.cover;

import gregtech.api.configuration.ConfigurationData;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The kind of value a {@link CoverSetting} holds: how it is stored in saved data and in a configuration card, which
 * values are allowed, and its ModularUI sync value. Both stores use the same tag type. Loading saved data is lenient
 * and reports a missing or unusable value as null; reading a card is strict and rejects it.
 *
 * @param <C> the cover the bounds are read from
 * @param <T> the value
 */
public interface CoverSettingType<C, T> {

    /**
     * Writes a value.
     *
     * @param tag   the tag to write to
     * @param key   the key to write under
     * @param value the value
     */
    void write(@NotNull NBTTagCompound tag, @NotNull String key, @NotNull T value);

    /**
     * Reads a value from saved data.
     *
     * @param cover the cover whose bounds apply
     * @param tag   the saved tag
     * @param key   the key the value is saved under
     * @return the value, or null when it is missing, not a number, or out of range for the cover
     */
    @Nullable
    T read(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key);

    /**
     * Reads a value from a configuration card.
     *
     * @param cover the cover whose bounds apply
     * @param tag   the card's settings
     * @param key   the key the value is copied under
     * @return the value
     * @throws IllegalArgumentException when the value is missing, of another tag type or out of range for the cover
     */
    @NotNull
    T readExact(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key);

    /**
     * @param getter reads the value
     * @param setter changes the value
     * @return an {@link IntSyncValue}, {@link BooleanSyncValue} or {@link EnumSyncValue} over the getter and setter
     */
    @NotNull
    ValueSyncHandler<T> syncValue(@NotNull Supplier<T> getter, @NotNull Consumer<T> setter);

    /**
     * An int saved as an int tag.
     *
     * @param min the least value the cover allows
     * @param max the greatest value the cover allows
     */
    static <C> @NotNull CoverSettingType<C, Integer> integer(@NotNull ToIntFunction<C> min,
                                                             @NotNull ToIntFunction<C> max) {
        return new CoverSettingType<>() {

            @Override
            public void write(@NotNull NBTTagCompound tag, @NotNull String key, @NotNull Integer value) {
                tag.setInteger(key, value);
            }

            @Override
            public @Nullable Integer read(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key) {
                if (!tag.hasKey(key, Constants.NBT.TAG_ANY_NUMERIC)) return null;
                int value = tag.getInteger(key);
                return value >= min.applyAsInt(cover) && value <= max.applyAsInt(cover) ? value : null;
            }

            @Override
            public @NotNull Integer readExact(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key) {
                return ConfigurationData.integer(tag, key, min.applyAsInt(cover), max.applyAsInt(cover));
            }

            @Override
            public @NotNull ValueSyncHandler<Integer> syncValue(@NotNull Supplier<Integer> getter,
                                                                @NotNull Consumer<Integer> setter) {
                return new IntSyncValue(getter::get, setter::accept);
            }
        };
    }

    /** A boolean saved as a byte tag. */
    static <C> @NotNull CoverSettingType<C, Boolean> bool() {
        return new CoverSettingType<>() {

            @Override
            public void write(@NotNull NBTTagCompound tag, @NotNull String key, @NotNull Boolean value) {
                tag.setBoolean(key, value);
            }

            @Override
            public @Nullable Boolean read(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key) {
                return tag.hasKey(key, Constants.NBT.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : null;
            }

            @Override
            public @NotNull Boolean readExact(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key) {
                return ConfigurationData.bool(tag, key);
            }

            @Override
            public @NotNull ValueSyncHandler<Boolean> syncValue(@NotNull Supplier<Boolean> getter,
                                                                @NotNull Consumer<Boolean> setter) {
                return new BooleanSyncValue(getter::get, setter::accept);
            }
        };
    }

    /**
     * An enum constant saved as its ordinal in an int tag.
     *
     * @param values the constants in ordinal order, at least one
     */
    static <C, E extends Enum<E>> @NotNull CoverSettingType<C, E> enumeration(E @NotNull [] values) {
        if (values.length == 0) throw new IllegalArgumentException("An enum setting needs at least one constant");
        Class<E> enumClass = values[0].getDeclaringClass();
        return new CoverSettingType<>() {

            @Override
            public void write(@NotNull NBTTagCompound tag, @NotNull String key, @NotNull E value) {
                tag.setInteger(key, value.ordinal());
            }

            @Override
            public @Nullable E read(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key) {
                if (!tag.hasKey(key, Constants.NBT.TAG_ANY_NUMERIC)) return null;
                int ordinal = tag.getInteger(key);
                return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
            }

            @Override
            public @NotNull E readExact(@NotNull C cover, @NotNull NBTTagCompound tag, @NotNull String key) {
                return values[ConfigurationData.integer(tag, key, 0, values.length - 1)];
            }

            @Override
            public @NotNull ValueSyncHandler<E> syncValue(@NotNull Supplier<E> getter, @NotNull Consumer<E> setter) {
                return new EnumSyncValue<>(enumClass, getter, setter);
            }
        };
    }
}

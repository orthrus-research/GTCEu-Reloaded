package gregtech.api.cover;

import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One configurable setting of a cover, declared once: its name, the key it is saved under, the key the configuration
 * card copies it under, its type with bounds or constants, its default, and how to read and change it. A cover lists
 * its settings in a {@link CoverSettings}, which saves, loads and copies all of them, and builds their sync values
 * with {@link #syncValue(Cover)}.
 * <p>
 * Loading saved data uses the loader given to {@link #saved(String, BiConsumer)}, which only assigns the value: the
 * cover is not attached yet and must not notify anything. The card and the sync values use the setter, the one the
 * cover's own screen and tools use.
 *
 * @param <C> the cover
 * @param <T> the value
 */
public final class CoverSetting<C extends Cover, T> {

    private final String name;
    private final CoverSettingType<C, T> type;
    private final Function<C, T> defaultValue;
    private final Function<C, T> getter;
    private final BiConsumer<C, T> setter;
    private final @Nullable String nbtKey;
    private final @Nullable BiConsumer<C, T> loader;
    private final @Nullable String cardKey;

    private CoverSetting(@NotNull String name, @NotNull CoverSettingType<C, T> type,
                         @NotNull Function<C, T> defaultValue, @NotNull Function<C, T> getter,
                         @NotNull BiConsumer<C, T> setter, @Nullable String nbtKey,
                         @Nullable BiConsumer<C, T> loader, @Nullable String cardKey) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.getter = getter;
        this.setter = setter;
        this.nbtKey = nbtKey;
        this.loader = loader;
        this.cardKey = cardKey;
    }

    /**
     * Declares a setting that is neither saved nor copied yet; see {@link #saved(String, BiConsumer)} and
     * {@link #card(String)}.
     *
     * @param name         the setting's name, which is also its sync value's name
     * @param type         the kind of value with its bounds
     * @param defaultValue the value a cover loads when its saved value is missing or unusable
     * @param getter       reads the cover's current value
     * @param setter       changes it, as the cover's screen does
     */
    public static <C extends Cover, T> @NotNull CoverSetting<C, T> of(@NotNull String name,
                                                                      @NotNull CoverSettingType<C, T> type,
                                                                      @NotNull Function<C, T> defaultValue,
                                                                      @NotNull Function<C, T> getter,
                                                                      @NotNull BiConsumer<C, T> setter) {
        return new CoverSetting<>(name, type, defaultValue, getter, setter, null, null, null);
    }

    /**
     * @param key    the key the value is saved under in the cover's NBT
     * @param loader assigns a loaded value without notifying anything
     * @return this setting, saved under {@code key}
     */
    public @NotNull CoverSetting<C, T> saved(@NotNull String key, @NotNull BiConsumer<C, T> loader) {
        return new CoverSetting<>(name, type, defaultValue, getter, setter, Objects.requireNonNull(key),
                Objects.requireNonNull(loader), cardKey);
    }

    /**
     * @param key the key the configuration card copies the value under
     * @return this setting, copied by the card under {@code key}
     */
    public @NotNull CoverSetting<C, T> card(@NotNull String key) {
        return new CoverSetting<>(name, type, defaultValue, getter, setter, nbtKey, loader,
                Objects.requireNonNull(key));
    }

    public @NotNull String getName() {
        return name;
    }

    /** @return the key the value is saved under, or null when it is not saved */
    public @Nullable String getNbtKey() {
        return nbtKey;
    }

    /** @return the key the configuration card copies the value under, or null when the card does not copy it */
    public @Nullable String getCardKey() {
        return cardKey;
    }

    public @NotNull T get(@NotNull C cover) {
        return getter.apply(cover);
    }

    public void set(@NotNull C cover, @NotNull T value) {
        setter.accept(cover, value);
    }

    public @NotNull T getDefault(@NotNull C cover) {
        return defaultValue.apply(cover);
    }

    /** Saves the cover's value, if this setting is saved. */
    public void writeToNBT(@NotNull C cover, @NotNull NBTTagCompound tag) {
        if (nbtKey != null) type.write(tag, nbtKey, get(cover));
    }

    /** Loads the saved value, or the default when it is missing or unusable, if this setting is saved. */
    public void readFromNBT(@NotNull C cover, @NotNull NBTTagCompound tag) {
        if (nbtKey == null || loader == null) return;
        T value = type.read(cover, tag, nbtKey);
        loader.accept(cover, value == null ? getDefault(cover) : value);
    }

    /** Writes the cover's value to a card's settings, if the card copies this setting. */
    public void capture(@NotNull C cover, @NotNull NBTTagCompound settings) {
        if (cardKey != null) type.write(settings, cardKey, get(cover));
    }

    /**
     * Checks a card's value without changing the cover, if the card copies this setting.
     *
     * @throws IllegalArgumentException when the value is missing, of another type or out of range
     */
    public void validate(@NotNull C cover, @NotNull NBTTagCompound settings) {
        if (cardKey != null) type.readExact(cover, settings, cardKey);
    }

    /** Sets the card's value through the setter, if the card copies this setting. */
    public void apply(@NotNull C cover, @NotNull NBTTagCompound settings) {
        if (cardKey != null) set(cover, type.readExact(cover, settings, cardKey));
    }

    /**
     * @param cover the cover whose screen is built
     * @return a sync value over the getter and setter: an {@code IntSyncValue} for an int setting, a
     *         {@code BooleanSyncValue} for a boolean one and an {@code EnumSyncValue} for an enum one
     */
    @SuppressWarnings("unchecked")
    public <S extends ValueSyncHandler<T>> @NotNull S syncValue(@NotNull C cover) {
        return (S) type.syncValue(() -> get(cover), value -> set(cover, value));
    }

    @Override
    public String toString() {
        return "CoverSetting{" + name + '}';
    }
}

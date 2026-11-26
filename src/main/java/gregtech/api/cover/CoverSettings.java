package gregtech.api.cover;

import gregtech.api.configuration.ConfigurationAdapter;
import gregtech.api.configuration.ConfigurationData;
import gregtech.api.configuration.ScalarConfigurationAdapter;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The configurable settings of one kind of cover. Its NBT, its configuration card adapter and its sync values all
 * come from the same {@link CoverSetting} declarations, so adding a setting is one declaration.
 * <p>
 * Loading falls back to each setting's default for a missing, mistyped or out-of-range value. The card is strict:
 * its settings must have exactly the card keys, each of its tag type and in range.
 *
 * @param <C> the cover
 */
public final class CoverSettings<C extends Cover> {

    private final List<CoverSetting<C, ?>> settings;
    private final String[] cardKeys;

    private CoverSettings(@NotNull List<CoverSetting<C, ?>> settings) {
        this.settings = settings;
        this.cardKeys = settings.stream()
                .map(CoverSetting::getCardKey)
                .filter(Objects::nonNull)
                .toArray(String[]::new);
    }

    /**
     * @param settings the settings, whose names, NBT keys and card keys are each distinct
     * @throws IllegalArgumentException when two settings share a name or key
     */
    @SafeVarargs
    public static <C extends Cover> @NotNull CoverSettings<C> of(@NotNull CoverSetting<C, ?>... settings) {
        Set<String> names = new HashSet<>();
        Set<String> nbtKeys = new HashSet<>();
        Set<String> cardKeys = new HashSet<>();
        for (CoverSetting<C, ?> setting : settings) {
            requireDistinct(names, setting.getName(), "name");
            if (setting.getNbtKey() != null) requireDistinct(nbtKeys, setting.getNbtKey(), "NBT key");
            if (setting.getCardKey() != null) requireDistinct(cardKeys, setting.getCardKey(), "card key");
        }
        return new CoverSettings<>(List.copyOf(Arrays.asList(settings)));
    }

    private static void requireDistinct(@NotNull Set<String> seen, @NotNull String value, @NotNull String what) {
        if (!seen.add(value)) {
            throw new IllegalArgumentException("Two cover settings have the " + what + " " + value);
        }
    }

    public @NotNull @Unmodifiable List<CoverSetting<C, ?>> getSettings() {
        return settings;
    }

    /** Saves every saved setting of the cover. */
    public void writeToNBT(@NotNull C cover, @NotNull NBTTagCompound tag) {
        for (CoverSetting<C, ?> setting : settings) {
            setting.writeToNBT(cover, tag);
        }
    }

    /** Loads every saved setting of the cover, each falling back to its default. */
    public void readFromNBT(@NotNull C cover, @NotNull NBTTagCompound tag) {
        for (CoverSetting<C, ?> setting : settings) {
            setting.readFromNBT(cover, tag);
        }
    }

    /** @return the card's settings for the cover */
    public @NotNull NBTTagCompound capture(@NotNull C cover) {
        NBTTagCompound data = new NBTTagCompound();
        for (CoverSetting<C, ?> setting : settings) {
            setting.capture(cover, data);
        }
        return data;
    }

    /**
     * Checks a card's settings without changing the cover.
     *
     * @throws IllegalArgumentException when a key is missing or unknown, or a value is mistyped or out of range
     */
    public void validate(@NotNull C cover, @NotNull NBTTagCompound data) {
        ConfigurationData.keys(data, cardKeys);
        for (CoverSetting<C, ?> setting : settings) {
            setting.validate(cover, data);
        }
    }

    /**
     * Sets a card's settings through each setting's setter on the server, then marks the cover dirty.
     *
     * @throws IllegalArgumentException when the cover is not in a server world or the settings are invalid
     */
    public void apply(@NotNull C cover, @NotNull NBTTagCompound data) {
        ConfigurationData.require(cover.getWorld() != null && !cover.getWorld().isRemote);
        validate(cover, data);
        for (CoverSetting<C, ?> setting : settings) {
            setting.apply(cover, data);
        }
        cover.markDirty();
    }

    /**
     * @param cover  the cover the card copies settings from and to
     * @param schema the card schema, which changes whenever the card keys or their meaning change
     * @return a configuration card adapter for the card settings
     */
    public @NotNull ConfigurationAdapter adapter(@NotNull C cover, @NotNull String schema) {
        return new ScalarConfigurationAdapter(schema) {

            @Override
            public NBTTagCompound capture() {
                return CoverSettings.this.capture(cover);
            }

            @Override
            protected void validate(NBTTagCompound settings) {
                CoverSettings.this.validate(cover, settings);
            }

            @Override
            protected void apply(NBTTagCompound settings) {
                CoverSettings.this.apply(cover, settings);
            }
        };
    }
}

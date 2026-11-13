package gregtech.api.util;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class LocalizationUtils {

    private static final Pattern NEW_LINE_PATTERN = Pattern.compile("/n");
    private static final String OWN_LANG_FILE = "/assets/gregtech/lang/en_us.lang";
    private static volatile Set<String> ownKeys;

    /**
     * This function calls {@link net.minecraft.client.resources.I18n#format(String, Object...)} when called on client
     * or {@link net.minecraft.util.text.translation.I18n#translateToLocalFormatted(String, Object...)} when called on
     * server.
     * <ul>
     * <li>It is intended that translations should be done using {@link net.minecraft.client.resources.I18n} on the
     * client.</li>
     * <li>For setting up translations on the server you should use
     * {@link net.minecraft.util.text.TextComponentTranslation}.</li>
     * <li>{@code LocalizationUtils} is only for cases where some kind of translation is required on the server and
     * there is no client/player in context.</li>
     * <li>{@code LocalizationUtils} is "best effort" and will probably only work properly with {@code en-us}.</li>
     * </ul>
     *
     * @param key the localization key passed to the underlying hasKey function
     * @return a boolean indicating if the given localization key has localisations
     */
    /**
     * GregTech's translations mark line breaks with {@code /n}. Text from other mods may contain {@code /n} for
     * other reasons (a unit such as m/n, a path, a URL), so that marker is only honoured for keys GregTech defines.
     *
     * @return whether the key is one of GregTech's own translation keys
     */
    public static boolean isOwnKey(@Nullable String key) {
        if (key == null) return false;
        Set<String> keys = ownKeys;
        if (keys == null) {
            keys = loadOwnKeys();
            ownKeys = keys;
        }
        return keys.contains(key);
    }

    @NotNull
    private static Set<String> loadOwnKeys() {
        Set<String> keys = new HashSet<>();
        InputStream in = LocalizationUtils.class.getResourceAsStream(OWN_LANG_FILE);
        if (in == null) return keys;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#")) keys.add(line.substring(0, equals).trim());
            }
        } catch (IOException e) {
            GTLog.logger.warn("Could not read GregTech's translation keys", e);
        }
        return keys;
    }

    public static String format(String key, Object... format) {
        if (FMLCommonHandler.instance().getSide() == Side.SERVER) {
            return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(key, format);
        } else {
            return net.minecraft.client.resources.I18n.format(key, format);
        }
    }

    /**
     * This function calls {@link net.minecraft.client.resources.I18n#hasKey(String)} when called on client
     * or {@link net.minecraft.util.text.translation.I18n#canTranslate(String)} when called on server.
     * <ul>
     * <li>It is intended that translations should be done using {@link net.minecraft.client.resources.I18n} on the
     * client.</li>
     * <li>For setting up translations on the server you should use
     * {@link net.minecraft.util.text.TextComponentTranslation}.</li>
     * <li>{@code LocalizationUtils} is only for cases where some kind of translation is required on the server and
     * there is no client/player in context.</li>
     * <li>{@code LocalizationUtils} is "best effort" and will probably only work properly with {@code en-us}.</li>
     * </ul>
     *
     * @param key the localization key passed to the underlying hasKey function
     * @return a boolean indicating if the given localization key has localisations
     */
    public static boolean hasKey(String key) {
        if (FMLCommonHandler.instance().getSide() == Side.SERVER) {
            return net.minecraft.util.text.translation.I18n.canTranslate(key);
        } else {
            return net.minecraft.client.resources.I18n.hasKey(key);
        }
    }

    /**
     * Returns translated text corresponding to given key {@code key} on current locale,
     * split with text {@code '\n'}.
     *
     * @param key  localization key
     * @param args substitutions
     * @return translated text split with text {@code '\n'}
     */
    @NotNull
    public static String[] formatLines(String key, Object... args) {
        return NEW_LINE_PATTERN.split(format(key, args));
    }
}

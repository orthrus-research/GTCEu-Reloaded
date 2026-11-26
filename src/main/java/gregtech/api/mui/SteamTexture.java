package gregtech.api.mui;

import com.cleanroommc.modularui.drawable.UITexture;
import org.jetbrains.annotations.NotNull;

/**
 * A texture drawn in a bronze and a steel variant, picked by a steam machine's pressure.
 *
 * @param bronze the texture of the low pressure, bronze machines
 * @param steel  the texture of the high pressure, steel machines
 */
public record SteamTexture(@NotNull UITexture bronze, @NotNull UITexture steel) {

    /**
     * @param highPressure whether the machine is a high pressure, steel one
     * @return the texture of its variant
     */
    public @NotNull UITexture get(boolean highPressure) {
        return highPressure ? steel : bronze;
    }
}

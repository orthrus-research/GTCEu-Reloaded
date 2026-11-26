package gregtech.api.mui.widget;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.LocalizationUtils;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * An icon shown while a machine cannot run its recipe, with the reason in its tooltip: the machine's missing energy or
 * steam icon when it lacks that, otherwise an information icon. The server's reason reaches the screen only while it
 * is open and only when it changes.
 */
public class RecipeFailureWidget extends Widget<RecipeFailureWidget> {

    private final Supplier<RecipeFailure> failure;
    private final UITexture energyTexture;

    /**
     * @param failure       the reason shown, as the screen has it
     * @param energyTexture the icon shown when the machine lacks energy or steam
     */
    public RecipeFailureWidget(@NotNull Supplier<RecipeFailure> failure, @NotNull UITexture energyTexture) {
        this.failure = failure;
        this.energyTexture = energyTexture;
        size(18);
        overlay(new DynamicDrawable(this::getIcon));
        tooltipAutoUpdate(true);
        tooltipBuilder(tooltip -> tooltip.addLine(IKey.str(failure.get().format(LocalizationUtils::format))));
        setEnabledIf(widget -> !failure.get().isEmpty());
    }

    /**
     * Create the indicator of a machine's recipe failure, with the sync value that brings the server's reason to the
     * screen registered with the panel.
     *
     * @param syncManager   the panel's sync manager
     * @param failure       the machine's reason, read on the server
     * @param energyTexture the icon shown when the machine lacks energy or steam
     * @return the indicator
     */
    public static @NotNull RecipeFailureWidget create(@NotNull PanelSyncManager syncManager,
                                                      @NotNull Supplier<RecipeFailure> failure,
                                                      @NotNull UITexture energyTexture) {
        GenericSyncValue<RecipeFailure> value = createSyncValue(failure);
        syncManager.syncValue("recipe_failure", value);
        return new RecipeFailureWidget(value::getValue, energyTexture);
    }

    /**
     * @param failure the machine's reason, read on the server
     * @return a sync value that sends the reason whenever it changes
     */
    public static @NotNull GenericSyncValue<RecipeFailure> createSyncValue(@NotNull Supplier<RecipeFailure> failure) {
        return GenericSyncValue.builder(RecipeFailure.class)
                .getter(failure)
                .serializer((buffer, value) -> value.write(buffer))
                .deserializer(RecipeFailure::read)
                .equalsDefault()
                .copyImmutable()
                .build();
    }

    /** @return the icon of the reason shown */
    public @NotNull IDrawable getIcon() {
        RecipeFailureReason reason = failure.get().getReason();
        return reason == RecipeFailureReason.NOT_ENOUGH_ENERGY || reason == RecipeFailureReason.NOT_ENOUGH_STEAM ?
                energyTexture : GTGuiTextures.INFO_ICON;
    }
}

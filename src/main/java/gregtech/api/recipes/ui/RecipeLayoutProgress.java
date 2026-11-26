package gregtech.api.recipes.ui;

import gregtech.api.recipes.RecipeMap;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

/**
 * A progress bar of a recipe map's screen. Its texture holds the empty bar in its upper half and the full bar in its
 * lower half; the full bar is drawn over the empty one as far as the progress, from 0 to 1, reaches in its direction.
 *
 * @param x         the left edge
 * @param y         the top edge
 * @param width     the width drawn
 * @param height    the height drawn
 * @param texture   the empty bar above the full one
 * @param direction the direction the bar fills in
 * @param progress  the progress shown, from 0 to 1
 * @param recipeMap the recipe map whose recipes a click on the bar shows, or null when a click does nothing
 */
public record RecipeLayoutProgress(int x, int y, int width, int height, @NotNull UITexture texture,
                                   @NotNull ProgressWidget.Direction direction, @NotNull DoubleSupplier progress,
                                   @Nullable RecipeMap<?> recipeMap)
        implements RecipeLayoutElement {}

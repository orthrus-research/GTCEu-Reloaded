package gregtech.api.recipes.ui;

import com.cleanroommc.modularui.drawable.UITexture;
import org.jetbrains.annotations.NotNull;

/**
 * An image of a recipe map's screen, such as the anvil under the forge hammer's progress bar.
 *
 * @param x       the left edge
 * @param y       the top edge
 * @param width   the width drawn
 * @param height  the height drawn
 * @param texture the image
 */
public record RecipeLayoutImage(int x, int y, int width, int height, @NotNull UITexture texture)
        implements RecipeLayoutElement {}

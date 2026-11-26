package gregtech.api.recipes.ui;

/**
 * One part of a {@link RecipeMapLayout}: a slot or tank, a progress bar or an image, at a position on the screen.
 */
public sealed interface RecipeLayoutElement permits RecipeLayoutSlot, RecipeLayoutProgress, RecipeLayoutImage {

    /** @return the left edge, from the left of the screen */
    int x();

    /** @return the top edge, from the top of the screen */
    int y();

    /** @return the width drawn */
    int width();

    /** @return the height drawn */
    int height();
}

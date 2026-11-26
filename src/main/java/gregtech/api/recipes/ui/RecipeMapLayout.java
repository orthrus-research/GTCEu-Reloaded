package gregtech.api.recipes.ui;

import gregtech.api.recipes.RecipeMap;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Where a recipe machine's screen, or a recipe viewer's page of its recipes, places its slots, tanks, progress bars
 * and images. A {@link RecipeMapUI} lays out its map's machines and recipe viewer category, and machines with a layout
 * of their own, such as the steam machines, describe theirs with one too.
 * <p>
 * The elements are kept in the order they were added, which is the order they are drawn in.
 */
public final class RecipeMapLayout {

    private final int width;
    private final int height;
    private final List<RecipeLayoutElement> elements = new ArrayList<>();

    /**
     * @param width  the width of the screen
     * @param height the height of the screen
     */
    public RecipeMapLayout(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public @NotNull @UnmodifiableView List<RecipeLayoutElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /**
     * @param type the kind of element
     * @return the elements of that kind, in the order they were added
     */
    public <T extends RecipeLayoutElement> @NotNull List<T> getElements(@NotNull Class<T> type) {
        List<T> found = new ArrayList<>();
        for (RecipeLayoutElement element : elements) {
            if (type.isInstance(element)) found.add(type.cast(element));
        }
        return found;
    }

    /**
     * Add an item slot or a fluid tank.
     *
     * @param isOutput   whether it shows an output inventory
     * @param isFluid    whether it is a tank
     * @param index      the slot or tank index in its inventory
     * @param x          the left edge
     * @param y          the top edge
     * @param background the textures drawn under its contents, bottom first
     * @return this
     */
    public @NotNull RecipeMapLayout slot(boolean isOutput, boolean isFluid, int index, int x, int y,
                                         @NotNull UITexture... background) {
        elements.add(new RecipeLayoutSlot(isOutput, isFluid, index, x, y, List.of(background)));
        return this;
    }

    /**
     * Add a progress bar.
     *
     * @param texture   the empty bar above the full one
     * @param direction the direction the bar fills in
     * @param progress  the progress shown, from 0 to 1
     * @param recipeMap the recipe map whose recipes a click on the bar shows, or null when a click does nothing
     * @return this
     */
    public @NotNull RecipeMapLayout progressBar(int x, int y, int width, int height, @NotNull UITexture texture,
                                                @NotNull ProgressWidget.Direction direction,
                                                @NotNull DoubleSupplier progress, @Nullable RecipeMap<?> recipeMap) {
        elements.add(new RecipeLayoutProgress(x, y, width, height, texture, direction, progress, recipeMap));
        return this;
    }

    /**
     * Add an image.
     *
     * @return this
     */
    public @NotNull RecipeMapLayout image(int x, int y, int width, int height, @NotNull UITexture texture) {
        elements.add(new RecipeLayoutImage(x, y, width, height, texture));
        return this;
    }
}

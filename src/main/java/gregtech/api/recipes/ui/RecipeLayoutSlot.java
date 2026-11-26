package gregtech.api.recipes.ui;

import com.cleanroommc.modularui.drawable.UITexture;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An item slot or a fluid tank of a recipe map's screen, 18 pixels square.
 * <p>
 * An input takes items or fluids and gives them back; an output only gives them. A screen shows the slot of its
 * machine's import or export inventory with this index, and a recipe viewer the ingredient at this index.
 *
 * @param isOutput   whether it shows an output inventory
 * @param isFluid    whether it is a tank rather than an item slot
 * @param index      the slot or tank index in its inventory
 * @param x          the left edge
 * @param y          the top edge
 * @param background the textures drawn under its contents, bottom first
 */
public record RecipeLayoutSlot(boolean isOutput, boolean isFluid, int index, int x, int y,
                               @NotNull List<@NotNull UITexture> background)
        implements RecipeLayoutElement {

    public static final int SIZE = 18;

    public RecipeLayoutSlot {
        background = List.copyOf(background);
    }

    @Override
    public int width() {
        return SIZE;
    }

    @Override
    public int height() {
        return SIZE;
    }
}

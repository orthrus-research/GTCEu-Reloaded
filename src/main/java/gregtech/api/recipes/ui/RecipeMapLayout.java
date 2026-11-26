package gregtech.api.recipes.ui;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.mui.widget.MachineTankTooltip;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
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

    /**
     * Place this layout on a ModularUI 2 panel as a machine's screen, showing the machine's inventories.
     * <p>
     * Input slots take and give items, and items shift-clicked from the player's inventory go to them; output slots
     * only give items. Input tanks are filled and drained with a held fluid container, output tanks only drained into
     * one. A progress bar with a recipe map shows the map's recipes in the recipe viewer when clicked.
     *
     * @param parent       the panel or widget to place it on
     * @param importItems  the machine's item inputs
     * @param exportItems  the machine's item outputs
     * @param importFluids the machine's input tanks
     * @param exportFluids the machine's output tanks
     */
    public void addWidgets(@NotNull ParentWidget<?> parent, @NotNull IItemHandlerModifiable importItems,
                           @NotNull IItemHandlerModifiable exportItems, @NotNull IMultipleTankHandler importFluids,
                           @NotNull IMultipleTankHandler exportFluids) {
        SlotGroup inputs = new SlotGroup("recipe_inputs", 1, true).setAllowSorting(false);
        for (RecipeLayoutElement element : elements) {
            IWidget widget;
            if (element instanceof RecipeLayoutSlot slot && slot.isFluid()) {
                IMultipleTankHandler tanks = slot.isOutput() ? exportFluids : importFluids;
                widget = createTank(slot, tanks.getTankAt(slot.index()).getDelegate());
            } else if (element instanceof RecipeLayoutSlot slot) {
                ModularSlot modularSlot = new ModularSlot(slot.isOutput() ? exportItems : importItems, slot.index())
                        .accessibility(!slot.isOutput(), true);
                if (!slot.isOutput()) modularSlot.slotGroup(inputs);
                widget = new ItemSlot()
                        .slot(modularSlot)
                        .background(slot.background().toArray(new IDrawable[0]))
                        .pos(slot.x(), slot.y());
            } else if (element instanceof RecipeLayoutProgress bar) {
                widget = createProgressBar(bar);
            } else {
                RecipeLayoutImage image = (RecipeLayoutImage) element;
                widget = image.texture().asWidget().pos(image.x(), image.y()).size(image.width(), image.height());
            }
            parent.child(widget);
        }
    }

    private static @NotNull GTFluidSlot createTank(@NotNull RecipeLayoutSlot slot, @NotNull IFluidTank tank) {
        GTFluidSyncHandler syncHandler = GTFluidSlot.sync(tank)
                .accessibility(true, !slot.isOutput());
        return new GTFluidSlot()
                .syncHandler(syncHandler)
                .background(slot.background().toArray(new IDrawable[0]))
                .tooltipBuilder(tooltip -> MachineTankTooltip.addDetails(tooltip, syncHandler))
                .pos(slot.x(), slot.y())
                .size(RecipeLayoutSlot.SIZE);
    }

    private static @NotNull ProgressWidget createProgressBar(@NotNull RecipeLayoutProgress bar) {
        ProgressWidget widget = bar.recipeMap() == null ? new ProgressWidget() :
                new RecipeProgressWidget(bar.recipeMap());
        boolean vertical = bar.direction() == ProgressWidget.Direction.UP ||
                bar.direction() == ProgressWidget.Direction.DOWN;
        return widget.texture(bar.texture(), vertical ? bar.height() : bar.width())
                .direction(bar.direction())
                .value(new DoubleValue.Dynamic(bar.progress(), null))
                .pos(bar.x(), bar.y())
                .size(bar.width(), bar.height());
    }
}

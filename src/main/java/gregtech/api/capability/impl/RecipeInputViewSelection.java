package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeInputViewProvider;
import gregtech.api.capability.RecipeInputView;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Which input view, such as a paint group, a multiblock's recipe logic searches and runs with: the view selected for
 * one search with the revisions it was selected at, the output route that view's recipe would use, the route of the
 * running recipe, and the view the last recipe came from, which the next search tries first. Only the running
 * recipe's route is saved, under {@value #NBT_KEY}.
 * <p>
 * It holds the selection only. {@link MultiblockRecipeLogic} enumerates and validates the views and reports what goes
 * wrong.
 */
final class RecipeInputViewSelection {

    static final String NBT_KEY = "OutputRoute";

    private RecipeInputView selected;
    private long viewsRevision;
    private long providerRevision;
    private RecipeOutputRoute selectedRoute;
    private RecipeOutputRoute activeRoute = RecipeOutputRoute.LEGACY;
    private Object previousKey;

    /** @return the view of the search in progress, or null outside one */
    @Nullable
    RecipeInputView getSelected() {
        return selected;
    }

    /** @return the route the selected view's recipe would use, or null outside a search */
    @Nullable
    RecipeOutputRoute getSelectedRoute() {
        return selectedRoute;
    }

    /** Selects a view for the search in progress, at the revision of the views now and of the view's provider. */
    void select(RecipeInputView view, long viewsRevision) {
        this.selected = view;
        this.viewsRevision = viewsRevision;
        this.providerRevision = view.getProvider().getRecipeInputRevision();
    }

    /** Sets the route the selected view's recipe would use. */
    void selectRoute(RecipeOutputRoute route) {
        this.selectedRoute = route;
    }

    void clearSelection() {
        this.selected = null;
        this.selectedRoute = null;
    }

    /**
     * Only with a view selected.
     *
     * @return whether the inventories are the selected view's, neither the views nor the view's provider changed
     *         since it was selected, and the views still accept it
     */
    boolean isSelectionCurrent(IItemHandlerModifiable items, IMultipleTankHandler fluids,
                               IRecipeInputViewProvider views) {
        return selected.getItems() == items && selected.getFluids() == fluids &&
                viewsRevision == views.getRecipeInputRevision() &&
                providerRevision == selected.getProvider().getRecipeInputRevision() &&
                views.isRecipeInputViewValid(selected);
    }

    /**
     * @param running whether a recipe is running
     * @return where outputs go: the selected view's route during a search, the running recipe's route while one runs,
     *         and the controller's own outputs otherwise
     */
    RecipeOutputRoute getOutputRoute(boolean running) {
        return selectedRoute != null ? selectedRoute : running ? activeRoute : RecipeOutputRoute.LEGACY;
    }

    /** @return the route of the running recipe; the controller's own outputs when none runs */
    RecipeOutputRoute getActiveRoute() {
        return activeRoute;
    }

    /** Gives the recipe that starts now the selected route, or the controller's own outputs outside a search. */
    void startSelectedRoute() {
        this.activeRoute = selectedRoute == null ? RecipeOutputRoute.LEGACY : selectedRoute;
    }

    void endActiveRoute() {
        this.activeRoute = RecipeOutputRoute.LEGACY;
    }

    /** @return whether the last recipe came from the view with this selection key */
    boolean isPrevious(Object selectionKey) {
        return Objects.equals(previousKey, selectionKey);
    }

    void rememberPrevious(Object selectionKey) {
        this.previousKey = selectionKey;
    }

    void forgetPrevious() {
        this.previousKey = null;
    }

    /** Moves the view the last recipe came from to the front, keeping the order of every other candidate. */
    void preferPrevious(List<RecipeInputView> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            if (isPrevious(candidates.get(i).getSelectionKey())) {
                candidates.add(0, candidates.remove(i));
                break;
            }
        }
    }

    /** Forgets everything, as after the structure deforms. */
    void reset() {
        clearSelection();
        this.activeRoute = RecipeOutputRoute.LEGACY;
        this.previousKey = null;
    }

    /** Writes the running recipe's route. */
    void writeToNBT(NBTTagCompound tag) {
        tag.setTag(NBT_KEY, activeRoute.serialize());
    }

    /** Reads the running recipe's route and forgets the rest, which is never saved. */
    void readFromNBT(NBTTagCompound tag, boolean running) {
        clearSelection();
        this.previousKey = null;
        this.activeRoute = running && tag.hasKey(NBT_KEY) ? RecipeOutputRoute.deserialize(tag.getTag(NBT_KEY)) :
                RecipeOutputRoute.LEGACY;
    }
}

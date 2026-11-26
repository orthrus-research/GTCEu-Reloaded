package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IRecipeInputViewProvider;
import gregtech.api.capability.RecipeInputView;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeInputViewSelectionTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeInputView view(Views provider, Object key) {
        return new RecipeInputView(provider, key, new ItemStackHandler(), new FluidTankList(false), -1, false);
    }

    @Test
    void withoutASearchOrARunningRecipeOutputsGoToTheController() {
        RecipeInputViewSelection selection = new RecipeInputViewSelection();
        assertNull(selection.getSelected());
        assertNull(selection.getSelectedRoute());
        assertSame(RecipeOutputRoute.LEGACY, selection.getActiveRoute());
        assertSame(RecipeOutputRoute.LEGACY, selection.getOutputRoute(false));
        assertSame(RecipeOutputRoute.LEGACY, selection.getOutputRoute(true));
    }

    @Test
    void aSelectedViewIsCurrentUntilItsInventoriesRevisionsOrValidityChange() {
        Views views = new Views();
        Views provider = new Views();
        RecipeInputView view = view(provider, "a");
        RecipeInputViewSelection selection = new RecipeInputViewSelection();
        selection.select(view, views.revision);
        assertSame(view, selection.getSelected());
        assertTrue(selection.isSelectionCurrent(view.getItems(), view.getFluids(), views));
        assertFalse(selection.isSelectionCurrent(new ItemStackHandler(), view.getFluids(), views));
        assertFalse(selection.isSelectionCurrent(view.getItems(), new FluidTankList(false), views));

        views.revision++;
        assertFalse(selection.isSelectionCurrent(view.getItems(), view.getFluids(), views));
        selection.select(view, views.revision);
        provider.revision++;
        assertFalse(selection.isSelectionCurrent(view.getItems(), view.getFluids(), views),
                "The provider's revision is taken when the view is selected");
        selection.select(view, views.revision);
        views.valid = false;
        assertFalse(selection.isSelectionCurrent(view.getItems(), view.getFluids(), views));
    }

    @Test
    void theSelectedRouteLeadsDuringASearchAndTheActiveRouteWhileARecipeRuns() {
        RecipeInputViewSelection selection = new RecipeInputViewSelection();
        RecipeOutputRoute red = RecipeOutputRoute.painted(0xFF0000);
        selection.select(view(new Views(), "a"), 0);
        selection.selectRoute(red);
        assertSame(red, selection.getOutputRoute(false));
        assertSame(red, selection.getOutputRoute(true));

        selection.startSelectedRoute();
        selection.clearSelection();
        assertNull(selection.getSelected());
        assertSame(red, selection.getActiveRoute());
        assertSame(red, selection.getOutputRoute(true));
        assertSame(RecipeOutputRoute.LEGACY, selection.getOutputRoute(false));

        selection.endActiveRoute();
        assertSame(RecipeOutputRoute.LEGACY, selection.getOutputRoute(true));
        selection.startSelectedRoute();
        assertSame(RecipeOutputRoute.LEGACY, selection.getActiveRoute(), "A recipe started outside a search");
    }

    @Test
    void thePreviousViewIsTriedFirstAndTheOthersKeepTheirOrder() {
        Views provider = new Views();
        RecipeInputView a = view(provider, "a");
        RecipeInputView b = view(provider, "b");
        RecipeInputView c = view(provider, "c");
        RecipeInputViewSelection selection = new RecipeInputViewSelection();
        List<RecipeInputView> candidates = new ArrayList<>(Arrays.asList(a, b, c));
        selection.preferPrevious(candidates);
        assertEquals(Arrays.asList(a, b, c), candidates);

        selection.rememberPrevious("c");
        assertTrue(selection.isPrevious("c"));
        assertFalse(selection.isPrevious("a"));
        selection.preferPrevious(candidates);
        assertEquals(Arrays.asList(c, a, b), candidates);

        selection.forgetPrevious();
        assertFalse(selection.isPrevious("c"));
    }

    @Test
    void onlyTheRunningRecipesRouteIsSaved() {
        RecipeInputViewSelection saved = new RecipeInputViewSelection();
        RecipeOutputRoute blue = RecipeOutputRoute.painted(0x0000FF);
        saved.select(view(new Views(), "a"), 0);
        saved.selectRoute(blue);
        saved.startSelectedRoute();
        saved.clearSelection();
        NBTTagCompound tag = new NBTTagCompound();
        saved.writeToNBT(tag);
        assertEquals(Collections.singleton("OutputRoute"), tag.getKeySet());
        assertEquals(blue.serialize(), tag.getCompoundTag("OutputRoute"));

        RecipeInputViewSelection loaded = new RecipeInputViewSelection();
        loaded.rememberPrevious("a");
        loaded.select(view(new Views(), "b"), 0);
        loaded.readFromNBT(tag, true);
        assertNull(loaded.getSelected());
        assertNull(loaded.getSelectedRoute());
        assertFalse(loaded.isPrevious("a"));
        assertEquals(0x0000FF, loaded.getActiveRoute().color);
        assertTrue(loaded.getActiveRoute().painted);

        RecipeInputViewSelection idle = new RecipeInputViewSelection();
        idle.readFromNBT(tag, false);
        assertSame(RecipeOutputRoute.LEGACY, idle.getActiveRoute());
    }

    @Test
    void resettingForgetsTheSelectionTheRouteAndThePreviousView() {
        RecipeInputViewSelection selection = new RecipeInputViewSelection();
        selection.select(view(new Views(), "a"), 0);
        selection.selectRoute(RecipeOutputRoute.painted(1));
        selection.startSelectedRoute();
        selection.rememberPrevious("a");
        selection.reset();
        assertNull(selection.getSelected());
        assertSame(RecipeOutputRoute.LEGACY, selection.getActiveRoute());
        assertSame(RecipeOutputRoute.LEGACY, selection.getOutputRoute(true));
        assertFalse(selection.isPrevious("a"));
    }

    private static final class Views implements IRecipeInputViewProvider {

        long revision;
        boolean valid = true;

        @Override
        public List<RecipeInputView> getRecipeInputViews() {
            return Collections.emptyList();
        }

        @Override
        public long getRecipeInputRevision() {
            return revision;
        }

        @Override
        public boolean isRecipeInputViewValid(RecipeInputView view) {
            return valid;
        }
    }
}

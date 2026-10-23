package gregtech.api.capability.impl;

import gregtech.api.capability.IRecipeInputViewProvider;
import gregtech.api.capability.RecipeInputView;

import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collections;
import java.util.List;

final class PaintGroupTestProvider extends PaintGroupTestPart implements IRecipeInputViewProvider {

    final ItemStackHandler privateItems;
    final FluidTankList privateFluids = new FluidTankList(true, new FluidTank(1000));
    Object key = new Object();
    long revision;
    int starts;
    boolean available = true;
    boolean pruneOnStart;

    PaintGroupTestProvider(int color) {
        this(color, new ItemStackHandler(2));
    }

    PaintGroupTestProvider(int color, ItemStackHandler items) {
        super(false, color);
        this.privateItems = items;
    }

    void changed() {
        revision++;
        if (owner != null) owner.addNotifiedInput(getRecipeHandler());
    }

    @Override
    public List<RecipeInputView> getRecipeInputViews() {
        // Deliberately unpainted descriptor: routing must use the attached part's actual paint.
        return available ?
                Collections.singletonList(new RecipeInputView(this, key, privateItems, privateFluids, -1, true)) :
                Collections.emptyList();
    }

    @Override
    public long getRecipeInputRevision() {
        return revision;
    }

    @Override
    public boolean isRecipeInputViewValid(RecipeInputView view) {
        return owner != null && available && view.getProvider() == this && view.getSelectionKey() == key;
    }

    @Override
    public void onRecipeInputViewStarted(RecipeInputView view) {
        starts++;
        if (pruneOnStart) available = false;
        changed();
    }
}

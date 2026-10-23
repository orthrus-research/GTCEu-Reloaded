package gregtech.api.capability;

import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.Objects;

/** Immutable selection metadata around live paired handlers; never persist a descriptor or mutate its key. */
public final class RecipeInputView {

    private final IRecipeInputViewProvider provider;
    private final Object selectionKey;
    private final IItemHandlerModifiable items;
    private final IMultipleTankHandler fluids;
    private final int paintColor;
    private final boolean privateInputs;

    public RecipeInputView(IRecipeInputViewProvider provider, Object selectionKey, IItemHandlerModifiable items,
                           IMultipleTankHandler fluids, int paintColor, boolean privateInputs) {
        if (paintColor < -1 || paintColor > 0xFFFFFF) throw new IllegalArgumentException("Invalid input paint color");
        this.provider = Objects.requireNonNull(provider);
        this.selectionKey = Objects.requireNonNull(selectionKey);
        this.items = Objects.requireNonNull(items);
        this.fluids = Objects.requireNonNull(fluids);
        this.paintColor = paintColor;
        this.privateInputs = privateInputs;
    }

    public IRecipeInputViewProvider getProvider() {
        return provider;
    }

    public Object getSelectionKey() {
        return selectionKey;
    }

    public IItemHandlerModifiable getItems() {
        return items;
    }

    public IMultipleTankHandler getFluids() {
        return fluids;
    }

    public int getPaintColor() {
        return paintColor;
    }

    public boolean hasPrivateInputs() {
        return privateInputs;
    }
}

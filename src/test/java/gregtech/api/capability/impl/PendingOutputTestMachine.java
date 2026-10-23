package gregtech.api.capability.impl;

import net.minecraftforge.items.IItemHandlerModifiable;

class PendingOutputTestMachine extends RecipeFailureTestMachine {

    boolean acceptItems = true;
    boolean acceptFluids = true;
    boolean voidItems;
    boolean voidFluids;

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new FilteredNotifiableItemStackHandler(this, 2, this, stack -> acceptItems);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return new FluidTankList(false, new FilteredNotifiableFluidTank(1000, this, () -> stack -> acceptFluids));
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return voidItems;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return voidFluids;
    }
}

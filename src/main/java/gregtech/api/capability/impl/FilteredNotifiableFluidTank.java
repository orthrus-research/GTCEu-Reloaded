package gregtech.api.capability.impl;

import gregtech.api.capability.IFilter;
import gregtech.api.capability.IFilteredFluidContainer;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Live insertion filtering with the same priority and notifications as ordinary multiblock tanks. */
public final class FilteredNotifiableFluidTank extends NotifiableFluidTank implements IFilteredFluidContainer {

    private final Supplier<IFilter<FluidStack>> filter;

    public FilteredNotifiableFluidTank(int capacity, MetaTileEntity owner, Supplier<IFilter<FluidStack>> filter) {
        super(capacity, owner, true);
        this.filter = filter;
    }

    @Override
    public @Nullable IFilter<FluidStack> getFilter() {
        return filter.get();
    }

    @Override
    public boolean canFillFluidType(FluidStack fluid) {
        IFilter<FluidStack> current = getFilter();
        return canFill() && (current == null || current.test(fluid));
    }

    public void notifyFilterChanged() {
        onContentsChanged();
    }
}

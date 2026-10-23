package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.util.function.BooleanSupplier;

public final class PatternBufferTank extends FluidTank {

    private final Runnable changed;
    private final BooleanSupplier available;
    private final boolean privateInputs;

    public PatternBufferTank(int capacity, boolean privateInputs, Runnable changed, BooleanSupplier available) {
        super(capacity);
        this.privateInputs = privateInputs;
        this.changed = changed;
        this.available = available;
    }

    @Override
    public FluidStack getFluid() {
        return fluid == null ? null : fluid.copy();
    }

    @Override
    public boolean canFillFluidType(FluidStack stack) {
        return !privateInputs && available.getAsBoolean() && stack != null &&
                PatternBufferCodec.fits(stack.writeToNBT(new NBTTagCompound()), PatternBufferCodec.MAX_STACK_BYTES);
    }

    @Override
    public boolean canDrainFluidType(FluidStack stack) {
        return available.getAsBoolean();
    }

    @Override
    public FluidStack drain(int amount, boolean doDrain) {
        return available.getAsBoolean() ? super.drain(amount, doDrain) : null;
    }

    @Override
    public FluidStack drain(FluidStack stack, boolean doDrain) {
        return available.getAsBoolean() ? super.drain(stack, doDrain) : null;
    }

    @Override
    public void setFluid(FluidStack stack) {
        if (!privateInputs && available.getAsBoolean() && (stack == null ||
                (stack.amount > 0 && stack.amount <= capacity && canFillFluidType(stack))))
            restore(stack);
    }

    void restore(FluidStack stack) {
        this.fluid = stack == null ? null : stack.copy();
        onContentsChanged();
    }

    @Override
    protected void onContentsChanged() {
        if (changed != null) changed.run();
    }
}

package gregtech.api.recipes.definition;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

/** Owns fluid identity, quantity and a deep copy of its tag. Accessors never expose the owned stack. */
public final class FluidValue {

    private final FluidStack prototype;
    private final NBTTagCompound serialized;

    private FluidValue(FluidStack stack) {
        prototype = Objects.requireNonNull(stack, "stack").copy();
        serialized = prototype.writeToNBT(new NBTTagCompound());
    }

    public static FluidValue of(FluidStack stack) {
        return new FluidValue(stack);
    }

    public FluidStack stack() {
        return prototype.copy();
    }

    public int amount() {
        return prototype.amount;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidValue value && serialized.equals(value.serialized);
    }

    @Override
    public int hashCode() {
        return serialized.hashCode();
    }
}

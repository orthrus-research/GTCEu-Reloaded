package gregtech.api.util.virtualregistry;

import gregtech.Bootstrap;
import gregtech.api.util.virtualregistry.entries.VirtualTank;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VirtualTankGuardTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    @Test
    public void overfullTankTakesNothing() {
        VirtualTank tank = new VirtualTank(1000);
        tank.setFluid(new FluidStack(FluidRegistry.WATER, 1500));
        assertEquals(0, tank.fill(new FluidStack(FluidRegistry.WATER, 100), true));
        assertEquals(1500, tank.getFluidAmount());
    }

    @Test
    public void badColourKeepsTheCurrentOne() {
        VirtualTank tank = new VirtualTank();
        tank.setColor("FF00FF00");
        tank.setColor("not a colour");
        tank.setColor("");
        assertEquals(0xFF00FF00, tank.getColor());
    }

    @Test
    public void missingCapacityKeepsTheDefault() {
        VirtualTank tank = new VirtualTank(5000);
        tank.deserializeNBT(new NBTTagCompound());
        assertEquals(5000, tank.getCapacity());
    }
}

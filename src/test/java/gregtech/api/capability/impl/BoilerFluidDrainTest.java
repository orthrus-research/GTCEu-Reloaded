package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.unification.material.Materials;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BoilerFluidDrainTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    private static FluidStack drain(IFluidHandler handler, int amount) throws Exception {
        Method method = BoilerRecipeLogic.class.getDeclaredMethod("getBoilerFluidFromContainer",
                IFluidHandler.class, int.class);
        method.setAccessible(true);
        return (FluidStack) method.invoke(null, handler, amount);
    }

    @Test
    public void shortWaterIsToppedUpFromDistilledWater() throws Exception {
        FluidTank water = new FluidTank(Materials.Water.getFluid(3), 1000);
        FluidTank distilled = new FluidTank(Materials.DistilledWater.getFluid(1000), 1000);

        FluidStack drained = drain(new FluidTankList(false, water, distilled), 4);
        assertNotNull(drained);
        assertEquals(4, drained.amount);
        assertEquals(0, water.getFluidAmount());
        assertEquals(999, distilled.getFluidAmount());
    }

    @Test
    public void waterAloneIsUsedWhenItSuffices() throws Exception {
        FluidTank water = new FluidTank(Materials.Water.getFluid(10), 1000);
        FluidTank distilled = new FluidTank(Materials.DistilledWater.getFluid(1000), 1000);

        assertEquals(4, drain(new FluidTankList(false, water, distilled), 4).amount);
        assertEquals(6, water.getFluidAmount());
        assertEquals(1000, distilled.getFluidAmount());
    }
}

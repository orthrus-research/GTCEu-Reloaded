package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiFluidFilterTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    private static FluidStack water() {
        return new FluidStack(FluidRegistry.WATER, 1000);
    }

    private static FluidStack lava() {
        return new FluidStack(FluidRegistry.LAVA, 1000);
    }

    @Test
    public void whitelistAcceptsOnlyListedFluids() {
        MultiFluidFilter filter = new MultiFluidFilter(false, water());
        assertTrue(filter.test(water()));
        assertFalse(filter.test(lava()));
    }

    @Test
    public void blacklistRejectsListedFluids() {
        MultiFluidFilter filter = new MultiFluidFilter(true, water());
        assertFalse(filter.test(water()));
        assertTrue(filter.test(lava()));
    }

    @Test
    public void negateFlipsTheResult() {
        MultiFluidFilter filter = new MultiFluidFilter(false, water());
        assertFalse(filter.negate().test(water()));
        assertTrue(filter.negate().test(lava()));
    }
}

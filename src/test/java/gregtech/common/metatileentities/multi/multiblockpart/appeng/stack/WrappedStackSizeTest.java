package gregtech.common.metatileentities.multi.multiblockpart.appeng.stack;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

public class WrappedStackSizeTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void fluidSizesSaturateInsteadOfWrapping() {
        WrappedFluidStack stack = WrappedFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, 1000));
        stack.incStackSize(Integer.MAX_VALUE);
        MatcherAssert.assertThat(stack.getStackSize(), is((long) Integer.MAX_VALUE));

        stack.setStackSize(5_000_000_000L);
        MatcherAssert.assertThat(stack.getStackSize(), is((long) Integer.MAX_VALUE));

        stack.setStackSize(10);
        stack.decStackSize(20);
        MatcherAssert.assertThat(stack.getStackSize(), is(0L));
    }

    @Test
    public void itemSizesSaturateInsteadOfWrapping() {
        WrappedItemStack stack = WrappedItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 64));
        stack.incStackSize(Integer.MAX_VALUE);
        MatcherAssert.assertThat(stack.getStackSize(), is((long) Integer.MAX_VALUE));

        stack.setStackSize(5_000_000_000L);
        MatcherAssert.assertThat(stack.getStackSize(), is((long) Integer.MAX_VALUE));
    }
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Return the amount actually accepted; callers retain the remainder. */
public interface PatternBufferRefund {

    int insertItem(ItemStack stack);

    int insertFluid(FluidStack stack);
}

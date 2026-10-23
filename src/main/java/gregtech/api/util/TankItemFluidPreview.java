package gregtech.api.util;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import org.jetbrains.annotations.Nullable;

/** Read-only inventory preview data for the two supported single-tank item families. */
public final class TankItemFluidPreview {

    public static final int BADGE_OFFSET = 1;
    public static final int BADGE_SIZE = 6;

    private TankItemFluidPreview() {}

    public static @Nullable FluidStack getFluid(ItemStack stack, boolean drums, boolean quantumTanks) {
        if ((!drums && !quantumTanks) || stack.isEmpty()) return null;
        MetaTileEntity machine = GTUtility.getMetaTileEntity(stack);
        if (machine == null) return null;
        boolean enabled = (drums && machine.getClass() == MetaTileEntityDrum.class) ||
                (quantumTanks && machine.getClass() == MetaTileEntityQuantumTank.class);
        if (!enabled) return null;

        // Read the item capability, never a registry prototype's mutable tank or its locked-fluid metadata.
        IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) return null;
        IFluidTankProperties[] tanks = handler.getTankProperties();
        if (tanks.length == 0 || tanks[0] == null) return null;
        FluidStack fluid = tanks[0].getContents();
        return fluid == null || fluid.amount <= 0 ? null : fluid.copy();
    }
}

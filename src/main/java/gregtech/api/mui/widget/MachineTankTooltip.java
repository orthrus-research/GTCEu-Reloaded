package gregtech.api.mui.widget;

import gregtech.api.mui.sync.GTFluidSyncHandler;

import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import org.jetbrains.annotations.NotNull;

/**
 * The lines a machine's tank adds below the fluid that every tank's tooltip names
 * ({@link GTFluidSyncHandler#handleTooltip}), as machine tanks showed them on the old GUI system: that the tank is
 * empty, its amount out of its capacity, and what clicking it with a fluid container does.
 */
public final class MachineTankTooltip {

    private MachineTankTooltip() {}

    /**
     * @param tooltip the tank's tooltip
     * @param tank    the tank's sync handler
     */
    public static void addDetails(@NotNull RichTooltip tooltip, @NotNull GTFluidSyncHandler tank) {
        FluidStack fluid = tank.getFluid();
        if (fluid == null) tooltip.addLine(IKey.lang("gregtech.fluid.empty"));
        tooltip.addLine(IKey.lang("gregtech.fluid.amount", fluid == null ? 0 : fluid.amount, tank.getCapacity()));

        // as the old tanks named it: filling a held container drains the tank, emptying one fills the tank
        String click = null;
        if (tank.canDrainSlot() && tank.canFillSlot()) click = "gregtech.fluid.click_combined";
        else if (tank.canDrainSlot()) click = "gregtech.fluid.click_to_fill";
        else if (tank.canFillSlot()) click = "gregtech.fluid.click_to_empty";
        if (click != null) {
            tooltip.emptyLine();
            tooltip.addLine(IKey.lang(click));
        }
    }
}

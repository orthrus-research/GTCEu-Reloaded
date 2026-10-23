package gregtech.api.metatileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class AutoOutputTestNeighbor extends TileEntity {

    IItemHandler items;
    IFluidHandler fluids;
    final List<EnumFacing> itemQueries = new ArrayList<>();
    final List<EnumFacing> fluidQueries = new ArrayList<>();
    Runnable onItemQuery;

    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            itemQueries.add(side);
            if (onItemQuery != null) onItemQuery.run();
            return items == null ? null : CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(items);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            fluidQueries.add(side);
            return fluids == null ? null : CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluids);
        }
        return super.getCapability(capability, side);
    }
}

package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.impl.FilteredFluidHandler;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import org.jetbrains.annotations.NotNull;

public class MetaTileEntityPassthroughHatchFluid extends MetaTileEntityPassthroughHatch<IFluidHandler> {

    private static final int TANK_SIZE = 16_000;

    private FluidTankList fluidTankList;

    public MetaTileEntityPassthroughHatchFluid(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, "FluidInventory", "gregtech.gui.fluid_passthrough");
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityPassthroughHatchFluid(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        FilteredFluidHandler[] fluidHandlers = new FilteredFluidHandler[getTier() + 1];
        for (int i = 0; i < fluidHandlers.length; i++) {
            fluidHandlers[i] = new FilteredFluidHandler(TANK_SIZE);
        }
        fluidInventory = fluidTankList = new FluidTankList(false, fluidHandlers);
        importHandler = new FluidHandlerProxy(fluidTankList, new FluidTankList(false));
        exportHandler = new FluidHandlerProxy(new FluidTankList(false), fluidTankList);
    }

    @Override
    protected void transferContents() {
        pushFluidsIntoNearbyHandlers(getFrontFacing().getOpposite()); // outputs to back
        pullFluidsFromNearbyHandlers(getFrontFacing()); // inputs from front
    }

    @Override
    protected Capability<IFluidHandler> getHandlerCapability() {
        return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;
    }

    @Override
    protected SimpleOverlayRenderer getHatchOverlay(boolean input) {
        return input ? Textures.FLUID_HATCH_INPUT_OVERLAY : Textures.FLUID_HATCH_OUTPUT_OVERLAY;
    }

    @Override
    protected int getRowSize() {
        return (int) Math.sqrt(getTier() + 1);
    }

    @Override
    protected IWidget createSlotWidget(int index) {
        return new GTFluidSlot().syncHandler(fluidTankList.getTankAt(index))
                .background(GTGuiTextures.FLUID_SLOT);
    }

    @Override
    protected int getWorkingButtonTop() {
        return 18 * 2;
    }

    @Override
    protected IDrawable getWorkingButtonOverlay() {
        return GTGuiTextures.BUTTON_FLUID_OUTPUT;
    }

    @Override
    protected NBTTagCompound serializeContents() {
        return fluidTankList.serializeNBT();
    }

    @Override
    protected void deserializeContents(NBTTagCompound tag) {
        this.fluidTankList.deserializeNBT(tag);
    }

    @Override
    protected String getCapacityTooltip() {
        return I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", getTier() + 1, TANK_SIZE);
    }

    @NotNull
    @Override
    public Class<IFluidHandler> getPassthroughType() {
        return IFluidHandler.class;
    }
}

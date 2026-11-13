package gregtech.common.covers;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.util.GTTransferUtils;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.covers.filter.CompositeFilter;
import gregtech.common.covers.filter.CompositeFilterOverflow;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SidedPosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class CoverFluidVoidingAdvanced extends CoverFluidVoiding {

    protected VoidingMode voidingMode = VoidingMode.VOID_ANY;
    protected int transferAmount = 0;

    public CoverFluidVoidingAdvanced(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                                     @NotNull EnumFacing attachedSide) {
        super(definition, coverableView, attachedSide);
    }

    @Override
    protected void doTransferFluids() {
        IFluidHandler myFluidHandler = getCoverableView().getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                getAttachedSide());
        if (myFluidHandler == null) {
            return;
        }
        switch (voidingMode) {
            case VOID_ANY -> GTTransferUtils.transferFluids(myFluidHandler, nullFluidTank, Integer.MAX_VALUE,
                    fluidFilterContainer::test);
            case VOID_OVERFLOW -> voidOverflow(myFluidHandler, fluidFilterContainer::test,
                    this.transferAmount);
        }
    }

    /**
     * Performs one tick worth of Keep Exact behavior.
     *
     * @param sourceHandler source(s) to move fluids from
     * @param fluidFilter   a predicate which determines what fluids may be moved
     * @param keepAmount    the desired amount in milliBuckets of a particular fluid in the destination
     */
    protected void voidOverflow(final IFluidHandler sourceHandler,
                                final Predicate<FluidStack> fluidFilter,
                                int keepAmount) {
        if (sourceHandler == null || fluidFilter == null)
            return;

        if (fluidFilterContainer.getFilter() instanceof CompositeFilter filter) {
            CompositeFilterOverflow.voidFluids(sourceHandler, filter, fluidFilterContainer.getTransferSize());
            return;
        }

        for (IFluidTankProperties tankProperties : sourceHandler.getTankProperties()) {
            FluidStack sourceFluid = tankProperties.getContents();
            if (sourceFluid == null || sourceFluid.amount == 0 ||
                    !getFluidFilterContainer().test(sourceFluid))
                continue;
            if (this.fluidFilterContainer.hasFilter() &&
                    voidingMode == VoidingMode.VOID_OVERFLOW) {
                keepAmount = this.fluidFilterContainer.getFilter()
                        .getTransferLimit(sourceFluid, getMaxTransferRate());
            }
            // a tank holding less than the keep amount has nothing to void
            int voidAmount = sourceFluid.amount - keepAmount;
            if (voidAmount <= 0) continue;
            // getContents may be the tank's live stack, so never write into it
            FluidStack toVoid = sourceFluid.copy();
            toVoid.amount = voidAmount;
            sourceHandler.drain(toVoid, true);
        }
    }

    public int getTransferAmount() {
        return this.fluidFilterContainer.getTransferSize();
    }

    public void setVoidingMode(VoidingMode transferMode) {
        this.voidingMode = transferMode;
        this.fluidFilterContainer.setMaxTransferSize(getMaxTransferRate());
        this.markDirty();
    }

    public VoidingMode getVoidingMode() {
        return voidingMode;
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        return super.buildUI(guiData, guiSyncManager, settings).height(192 + 20);
    }

    @Override
    protected Flow createUI(GuiData data, PanelSyncManager syncManager) {
        var voidingMode = new EnumSyncValue<>(VoidingMode.class, this::getVoidingMode, this::setVoidingMode);
        syncManager.syncValue("voiding_mode", voidingMode);

        var bucketMode = new EnumSyncValue<>(BucketMode.class, this::getBucketMode, this::setBucketMode);
        bucketMode.updateCacheFromSource(true);
        syncManager.syncValue("bucket_mode", bucketMode);

        var filterTransferSize = new IntSyncValue(
                fluidFilterContainer::getTransferSize, fluidFilterContainer::setTransferSize);
        filterTransferSize.updateCacheFromSource(true);
        syncManager.syncValue("filter_transfer_millibuckets", filterTransferSize);
        var transferTextField = new GTTextFieldWidget().widthRel(0.5f).right(0);
        transferTextField.setEnabled(this.fluidFilterContainer.showGlobalTransferLimitSlider() &&
                this.voidingMode == VoidingMode.VOID_OVERFLOW);

        return super.createUI(data, syncManager)
                .child(new EnumRowBuilder<>(VoidingMode.class)
                        .value(voidingMode)
                        .lang("cover.voiding.voiding_mode")
                        .overlay(16, GTGuiTextures.VOIDING_MODE_OVERLAY)
                        .build())
                .child(new EnumRowBuilder<>(BucketMode.class)
                        .value(bucketMode)
                        .overlay(IKey.str("kL"), IKey.str("L"))
                        .build()
                        .child(transferTextField
                                .setEnabledIf(w -> this.fluidFilterContainer.showGlobalTransferLimitSlider() &&
                                        this.voidingMode == VoidingMode.VOID_OVERFLOW)
                                .setNumbersDouble(value -> Math.clamp(value, bucketMode.getValue().displayAmount(1),
                                        bucketMode.getValue().displayAmount(fluidFilterContainer.getMaxTransferSize())))
                                .value(new StringValue.Dynamic(
                                        () -> bucketMode.getValue().formatRate(filterTransferSize.getValue()),
                                        value -> filterTransferSize.setValue(bucketMode.getValue().parseRate(value,
                                                fluidFilterContainer.getMaxTransferSize()), true, true)))
                                .setTextColor(Color.WHITE.darker(1))));
    }

    @Override
    protected int getMaxTransferRate() {
        return getVoidingMode().maxStackSize;
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.FLUID_VOIDING_ADVANCED.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public void readInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.readInitialSyncData(packetBuffer);
        this.voidingMode = VoidingMode.VALUES[packetBuffer.readByte()];
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.writeInitialSyncData(packetBuffer);
        packetBuffer.writeByte(this.voidingMode.ordinal());
    }

    @Override
    public void writeToNBT(@NotNull NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setInteger("VoidingMode", voidingMode.ordinal());
        tagCompound.setInteger("TransferAmount", transferAmount);
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tagCompound) {
        this.voidingMode = VoidingMode.VALUES[tagCompound.getInteger("VoidingMode")];
        this.fluidFilterContainer.setMaxTransferSize(this.voidingMode.maxStackSize);
        this.transferAmount = tagCompound.getInteger("TransferAmount");
        super.readFromNBT(tagCompound);
    }
}

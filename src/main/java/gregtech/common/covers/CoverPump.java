package gregtech.common.covers;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.impl.FluidHandlerDelegate;
import gregtech.api.configuration.ConfigurationAdapter;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverSetting;
import gregtech.api.cover.CoverSettingType;
import gregtech.api.cover.CoverSettings;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.items.toolitem.ToolAction;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTTransferUtils;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleSidedCubeRenderer;
import gregtech.common.covers.filter.FluidFilterContainer;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SidedPosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class CoverPump extends CoverBase implements CoverWithUI, ITickable, IControllable {

    /** Millibuckets moved per second, from 1 to the pump's maximum, which is also the default. */
    public static final CoverSetting<CoverPump, Integer> TRANSFER_RATE = CoverSetting
            .of("throughput_millibuckets", CoverSettingType.<CoverPump>integer(pump -> 1,
                    pump -> pump.maxFluidTransferRate), pump -> pump.maxFluidTransferRate,
                    CoverPump::getTransferRateMilliBuckets, (pump, rate) -> pump.setTransferRateMilliBuckets(rate))
            .saved("TransferRate", (pump, rate) -> pump.transferRate = rate)
            .card("Rate");
    public static final CoverSetting<CoverPump, PumpMode> PUMP_MODE = CoverSetting
            .of("pump_mode", CoverSettingType.<CoverPump, PumpMode>enumeration(PumpMode.VALUES),
                    pump -> PumpMode.EXPORT, CoverPump::getPumpMode, CoverPump::setPumpMode)
            .saved("PumpMode", (pump, mode) -> pump.pumpMode = mode)
            .card("Mode");
    public static final CoverSetting<CoverPump, ManualImportExportMode> MANUAL_IMPORT_EXPORT_MODE = CoverSetting
            .of("manual_io", CoverSettingType.<CoverPump, ManualImportExportMode>enumeration(
                    ManualImportExportMode.VALUES), pump -> ManualImportExportMode.DISABLED,
                    CoverPump::getManualImportExportMode, CoverPump::setManualImportExportMode)
            .saved("ManualImportExportMode", (pump, mode) -> pump.manualImportExportMode = mode)
            .card("Manual");
    public static final CoverSetting<CoverPump, DistributionMode> DISTRIBUTION_MODE = CoverSetting
            .of("distribution_mode", CoverSettingType.<CoverPump, DistributionMode>enumeration(
                    DistributionMode.VALUES), pump -> DistributionMode.INSERT_FIRST, pump -> pump.distributionMode,
                    (pump, mode) -> pump.distributionMode = mode)
            .saved("DistributionMode", (pump, mode) -> pump.distributionMode = mode)
            .card("Distribution");
    public static final CoverSetting<CoverPump, Boolean> WORKING_ENABLED = CoverSetting
            .of("working_enabled", CoverSettingType.<CoverPump>bool(), pump -> true,
                    CoverPump::isWorkingEnabled, CoverPump::setWorkingEnabled)
            .saved("WorkingAllowed", (pump, working) -> pump.isWorkingAllowed = working)
            .card("Working");
    /** The unit the screen shows the rate in; the rate itself is always kept in millibuckets. */
    public static final CoverSetting<CoverPump, BucketMode> BUCKET_MODE = CoverSetting
            .of("throughput_units", CoverSettingType.<CoverPump, BucketMode>enumeration(BucketMode.VALUES),
                    pump -> BucketMode.MILLI_BUCKET, CoverPump::getBucketMode, CoverPump::setBucketMode)
            .saved("BucketMode", (pump, mode) -> pump.bucketMode = mode)
            .card("Bucket");
    /** Everything a pump saves and its configuration card copies, apart from its filter. */
    public static final CoverSettings<CoverPump> SETTINGS = CoverSettings.of(TRANSFER_RATE, PUMP_MODE,
            MANUAL_IMPORT_EXPORT_MODE, DISTRIBUTION_MODE, WORKING_ENABLED, BUCKET_MODE);

    public final int tier;
    public final int maxFluidTransferRate;
    protected int transferRate;
    protected PumpMode pumpMode = PumpMode.EXPORT;
    protected ManualImportExportMode manualImportExportMode = ManualImportExportMode.DISABLED;
    protected DistributionMode distributionMode = DistributionMode.INSERT_FIRST;
    protected int fluidLeftToTransferLastSecond;
    private CoverableFluidHandlerWrapper fluidHandlerWrapper;
    protected boolean isWorkingAllowed = true;
    protected FluidFilterContainer fluidFilterContainer;
    protected BucketMode bucketMode = BucketMode.MILLI_BUCKET;

    public CoverPump(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                     @NotNull EnumFacing attachedSide, int tier, int mbPerTick) {
        super(definition, coverableView, attachedSide);
        this.tier = tier;
        this.maxFluidTransferRate = mbPerTick;
        this.transferRate = mbPerTick;
        this.fluidLeftToTransferLastSecond = transferRate;
        this.fluidFilterContainer = new FluidFilterContainer(this);
    }

    public void setStringTransferRate(String s) {
        try {
            this.fluidFilterContainer.setTransferSize(bucketMode.parseRate(s, Integer.MAX_VALUE));
        } catch (NumberFormatException | ArithmeticException ignored) {
            // not a number: keep the current size
        }
    }

    public String getStringTransferRate() {
        return bucketMode.formatRate(this.fluidFilterContainer.getTransferSize());
    }

    public void setTransferRate(int transferRate) {
        setTransferRateMilliBuckets((long) transferRate * bucketMode.milliBucketsPerUnit);
    }

    /** Canonical rate, independent of the authoring or display unit. */
    public int getTransferRateMilliBuckets() {
        return transferRate;
    }

    public void setTransferRateMilliBuckets(long milliBuckets) {
        this.transferRate = (int) Math.clamp(milliBuckets, 1, maxFluidTransferRate);
        markDirty();
    }

    /**
     * Copies this pump's settings with the configuration card. Subclasses inherit it; one with settings of its own, or
     * that gives these settings another meaning, overrides this to return its own adapter or null.
     */
    @Override
    public @Nullable ConfigurationAdapter getConfigurationAdapter() {
        // The card stores the actual mB rate; the unit only changes how the screen shows it.
        return SETTINGS.adapter(this, "gregtech:pump/1");
    }

    public int getTransferRate() {
        return bucketMode == BucketMode.BUCKET ? transferRate / 1000 : transferRate;
    }

    protected void adjustTransferRate(int amount) {
        setTransferRateMilliBuckets((long) transferRate + (long) amount * bucketMode.milliBucketsPerUnit);
    }

    public void setPumpMode(PumpMode pumpMode) {
        this.pumpMode = pumpMode;
        writeCustomData(GregtechDataCodes.UPDATE_COVER_MODE, buf -> buf.writeEnumValue(pumpMode));
        markDirty();
    }

    public PumpMode getPumpMode() {
        return pumpMode;
    }

    public void setBucketMode(BucketMode bucketMode) {
        this.bucketMode = java.util.Objects.requireNonNull(bucketMode);
        markDirty();
    }

    public BucketMode getBucketMode() {
        return bucketMode;
    }

    public ManualImportExportMode getManualImportExportMode() {
        return manualImportExportMode;
    }

    protected void setManualImportExportMode(ManualImportExportMode manualImportExportMode) {
        this.manualImportExportMode = manualImportExportMode;
        markDirty();
    }

    public FluidFilterContainer getFluidFilterContainer() {
        return fluidFilterContainer;
    }

    @Override
    public void update() {
        long timer = getOffsetTimer();
        if (isWorkingAllowed && fluidLeftToTransferLastSecond > 0) {
            this.fluidLeftToTransferLastSecond -= doTransferFluids(fluidLeftToTransferLastSecond);
        }
        if (timer % 20 == 0) {
            this.fluidLeftToTransferLastSecond = transferRate;
        }
    }

    protected int doTransferFluids(int transferLimit) {
        TileEntity tileEntity = getNeighbor(getAttachedSide());
        IFluidHandler fluidHandler = tileEntity == null ? null : tileEntity
                .getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, getAttachedSide().getOpposite());
        IFluidHandler myFluidHandler = getCoverableView().getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                getAttachedSide());
        if (fluidHandler == null || myFluidHandler == null) {
            return 0;
        }
        return doTransferFluidsInternal(myFluidHandler, fluidHandler, transferLimit);
    }

    protected int doTransferFluidsInternal(IFluidHandler myFluidHandler, IFluidHandler fluidHandler,
                                           int transferLimit) {
        if (pumpMode == PumpMode.IMPORT) {
            return GTTransferUtils.transferFluids(fluidHandler, myFluidHandler, transferLimit,
                    fluidFilterContainer::test);
        } else if (pumpMode == PumpMode.EXPORT) {
            return GTTransferUtils.transferFluids(myFluidHandler, fluidHandler, transferLimit,
                    fluidFilterContainer::test);
        }
        return 0;
    }

    protected boolean checkInputFluid(FluidStack fluidStack) {
        return fluidFilterContainer.test(fluidStack);
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var panel = GTGuis.createPanel(this, 176, createThroughputUnitRow() ? 210 : 192);

        getFluidFilterContainer().setMaxTransferSize(getMaxTransferRate());

        return panel.child(CoverWithUI.createTitleRow(getPickItem()))
                .child(createUI(guiData, guiSyncManager))
                .bindPlayerInventory();
    }

    protected Flow createUI(GuiData data, PanelSyncManager syncManager) {
        EnumSyncValue<ManualImportExportMode> manualIOmode = MANUAL_IMPORT_EXPORT_MODE.syncValue(this);

        IntSyncValue throughput = TRANSFER_RATE.syncValue(this);
        EnumSyncValue<BucketMode> units = BUCKET_MODE.syncValue(this);
        throughput.updateCacheFromSource(true);
        units.updateCacheFromSource(true);
        syncManager.syncValue(TRANSFER_RATE.getName(), throughput);
        syncManager.syncValue(BUCKET_MODE.getName(), units);

        EnumSyncValue<PumpMode> pumpMode = PUMP_MODE.syncValue(this);

        syncManager.syncValue(MANUAL_IMPORT_EXPORT_MODE.getName(), manualIOmode);
        syncManager.syncValue(PUMP_MODE.getName(), pumpMode);

        var column = Flow.column().top(24).margin(7, 0)
                .widthRel(1f).coverChildrenHeight();

        if (createThroughputRow())
            column.child(Flow.row().coverChildrenHeight()
                    .marginBottom(2).widthRel(1f)
                    .child(new ButtonWidget<>()
                            .left(0).width(18)
                            .onMousePressed(mouseButton -> {
                                long val = (long) throughput.getValue() -
                                        (long) getIncrementValue(MouseData.create(mouseButton)) *
                                                units.getValue().milliBucketsPerUnit;
                                throughput.setValue((int) Math.clamp(val, 1, maxFluidTransferRate), true, true);
                                return true;
                            })
                            .onUpdateListener(w -> w.overlay(createAdjustOverlay(false))))
                    .child(new GTTextFieldWidget()
                            .left(18).right(18)
                            .setPostFix(() -> units.getValue() == BucketMode.BUCKET ? " kL/s" : " L/s")
                            .setTextColor(Color.WHITE.darker(1))
                            .setNumbersDouble(value -> Math.clamp(value,
                                    1.0 / units.getValue().milliBucketsPerUnit,
                                    (double) maxFluidTransferRate / units.getValue().milliBucketsPerUnit))
                            .value(new StringValue.Dynamic(
                                    () -> units.getValue().formatRate(throughput.getValue()),
                                    value -> throughput.setValue(
                                            units.getValue().parseRate(value, maxFluidTransferRate), true, true)))
                            .background(GTGuiTextures.DISPLAY))
                    .child(new ButtonWidget<>()
                            .right(0).width(18)
                            .onMousePressed(mouseButton -> {
                                long val = (long) throughput.getValue() +
                                        (long) getIncrementValue(MouseData.create(mouseButton)) *
                                                units.getValue().milliBucketsPerUnit;
                                throughput.setValue((int) Math.clamp(val, 1, maxFluidTransferRate), true, true);
                                return true;
                            })
                            .onUpdateListener(w -> w.overlay(createAdjustOverlay(true)))));

        if (createThroughputUnitRow())
            column.child(new EnumRowBuilder<>(BucketMode.class).value(units)
                    .overlay(IKey.str("kL"), IKey.str("L")).build());

        if (createFilterRow())
            column.child(getFluidFilterContainer().initUI(data, syncManager));

        if (createManualIOModeRow())
            column.child(new EnumRowBuilder<>(ManualImportExportMode.class)
                    .value(manualIOmode)
                    .lang("cover.generic.manual_io")
                    .overlay(new IDrawable[] {
                            new DynamicDrawable(() -> pumpMode.getValue().isImport() ?
                                    GTGuiTextures.MANUAL_IO_OVERLAY_OUT[0] : GTGuiTextures.MANUAL_IO_OVERLAY_IN[0]),
                            new DynamicDrawable(() -> pumpMode.getValue().isImport() ?
                                    GTGuiTextures.MANUAL_IO_OVERLAY_OUT[1] : GTGuiTextures.MANUAL_IO_OVERLAY_IN[1]),
                            new DynamicDrawable(() -> pumpMode.getValue().isImport() ?
                                    GTGuiTextures.MANUAL_IO_OVERLAY_OUT[2] : GTGuiTextures.MANUAL_IO_OVERLAY_IN[2])
                    })
                    .build());

        if (createPumpModeRow())
            column.child(new EnumRowBuilder<>(PumpMode.class)
                    .value(pumpMode)
                    .lang("cover.pump.mode")
                    .overlay(GTGuiTextures.CONVEYOR_MODE_OVERLAY) // todo pump mode overlays
                    .build());

        return column;
    }

    protected boolean createThroughputRow() {
        return true;
    }

    protected boolean createThroughputUnitRow() {
        return createThroughputRow();
    }

    protected boolean createFilterRow() {
        return true;
    }

    protected boolean createManualIOModeRow() {
        return true;
    }

    protected boolean createPumpModeRow() {
        return true;
    }

    protected int getMaxTransferRate() {
        return 1;
    }

    /** Subclasses inherit this hint; one that changes what the screwdriver does overrides this. */
    @Override
    public @Nullable ToolAction getToolActionHint(Set<String> toolClasses, boolean sneaking) {
        return toolClasses.contains(ToolClasses.SCREWDRIVER) ? ToolAction.COVER_SETTINGS : null;
    }

    @Override
    public @NotNull EnumActionResult onScrewdriverClick(@NotNull EntityPlayer playerIn, @NotNull EnumHand hand,
                                                        @NotNull CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            openUI((EntityPlayerMP) playerIn);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void readCustomData(int discriminator, @NotNull PacketBuffer buf) {
        super.readCustomData(discriminator, buf);
        if (discriminator == GregtechDataCodes.UPDATE_COVER_MODE) {
            this.pumpMode = buf.readEnumValue(PumpMode.class);
            scheduleRenderUpdate();
        }
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.writeInitialSyncData(packetBuffer);
        packetBuffer.writeByte(pumpMode.ordinal());
        getFluidFilterContainer().writeInitialSyncData(packetBuffer);
    }

    @Override
    public void readInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.readInitialSyncData(packetBuffer);
        this.pumpMode = PumpMode.VALUES[packetBuffer.readByte()];
        getFluidFilterContainer().readInitialSyncData(packetBuffer);
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return coverable.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    @Override
    public boolean canInteractWithOutputSide() {
        return true;
    }

    @Override
    public void onRemoval() {
        dropInventoryContents(fluidFilterContainer);
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        if (pumpMode == PumpMode.EXPORT) {
            Textures.PUMP_OVERLAY.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
        } else {
            Textures.PUMP_OVERLAY_INVERTED.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
        }
    }

    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, T defaultValue) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (defaultValue == null) {
                return null;
            }
            IFluidHandler delegate = (IFluidHandler) defaultValue;
            if (fluidHandlerWrapper == null || fluidHandlerWrapper.delegate != delegate) {
                this.fluidHandlerWrapper = new CoverableFluidHandlerWrapper(delegate);
            }
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandlerWrapper);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return defaultValue;
    }

    @Override
    public boolean isWorkingEnabled() {
        return isWorkingAllowed;
    }

    @Override
    public void setWorkingEnabled(boolean isActivationAllowed) {
        this.isWorkingAllowed = isActivationAllowed;
        markDirty();
    }

    @Override
    public void writeToNBT(@NotNull NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        SETTINGS.writeToNBT(this, tagCompound);
        tagCompound.setTag("Filter", fluidFilterContainer.serializeNBT());
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        SETTINGS.readFromNBT(this, tagCompound);
        this.fluidFilterContainer.deserializeNBT(tagCompound.getCompoundTag("Filter"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected @NotNull TextureAtlasSprite getPlateSprite() {
        return Textures.VOLTAGE_CASINGS[this.tier].getSpriteOnSide(SimpleSidedCubeRenderer.RenderSide.SIDE);
    }

    public enum PumpMode implements IStringSerializable, IIOMode {

        IMPORT("cover.pump.mode.import"),
        EXPORT("cover.pump.mode.export");

        public static final PumpMode[] VALUES = values();
        public final String localeName;

        PumpMode(String localeName) {
            this.localeName = localeName;
        }

        @NotNull
        @Override
        public String getName() {
            return localeName;
        }

        @Override
        public boolean isImport() {
            return this == IMPORT;
        }
    }

    public enum BucketMode implements IStringSerializable {

        BUCKET("cover.bucket.mode.bucket", 1000),
        MILLI_BUCKET("cover.bucket.mode.milli_bucket", 1);

        public static final BucketMode[] VALUES = values();
        public final String localeName;
        private final int milliBucketsPerUnit;

        BucketMode(String localeName, int milliBucketsPerUnit) {
            this.localeName = localeName;
            this.milliBucketsPerUnit = milliBucketsPerUnit;
        }

        public String formatRate(int milliBuckets) {
            return java.math.BigDecimal.valueOf(milliBuckets)
                    .divide(java.math.BigDecimal.valueOf(milliBucketsPerUnit)).stripTrailingZeros().toPlainString();
        }

        public double displayAmount(int milliBuckets) {
            return (double) milliBuckets / milliBucketsPerUnit;
        }

        public int parseRate(String value, int maximum) {
            var amount = new java.math.BigDecimal(value.replace(",", ""))
                    .multiply(java.math.BigDecimal.valueOf(milliBucketsPerUnit));
            return amount.max(java.math.BigDecimal.ONE).min(java.math.BigDecimal.valueOf(maximum))
                    .setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        }

        @NotNull
        @Override
        public String getName() {
            return localeName;
        }
    }

    private class CoverableFluidHandlerWrapper extends FluidHandlerDelegate {

        public CoverableFluidHandlerWrapper(@NotNull IFluidHandler delegate) {
            super(delegate);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (pumpMode == PumpMode.EXPORT && manualImportExportMode == ManualImportExportMode.DISABLED) {
                return 0;
            }
            if (!checkInputFluid(resource) && manualImportExportMode == ManualImportExportMode.FILTERED) {
                return 0;
            }
            return super.fill(resource, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (pumpMode == PumpMode.IMPORT && manualImportExportMode == ManualImportExportMode.DISABLED) {
                return null;
            }
            if (manualImportExportMode == ManualImportExportMode.FILTERED && !checkInputFluid(resource)) {
                return null;
            }
            return super.drain(resource, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (pumpMode == PumpMode.IMPORT && manualImportExportMode == ManualImportExportMode.DISABLED) {
                return null;
            }
            if (manualImportExportMode == ManualImportExportMode.FILTERED) {
                FluidStack result = super.drain(maxDrain, false);
                if (result == null || result.amount <= 0 || !checkInputFluid(result)) {
                    return null;
                }
                return doDrain ? super.drain(maxDrain, true) : result;
            }
            return super.drain(maxDrain, doDrain);
        }
    }
}

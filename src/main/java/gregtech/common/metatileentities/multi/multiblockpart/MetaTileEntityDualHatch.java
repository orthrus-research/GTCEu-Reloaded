package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.*;
import gregtech.api.capability.impl.*;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.covers.filter.FluidFilterContainer;
import gregtech.common.covers.filter.ItemFilterContainer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** Physical item/fluid storage with one recipe identity; the ghost circuit is never an automation slot. */
public class MetaTileEntityDualHatch extends MetaTileEntityMultiblockPart
                                     implements IMultiblockAbilityPart<Object>, IControllable, IGhostSlotConfigurable {

    private final boolean isExportHatch;
    private final int rows;
    private final int tankCapacity;
    private final IItemHandlerModifiable physicalItems;
    private final FluidTankList physicalFluids;
    private final DualHandler recipeHandler;
    @Nullable
    private final GhostCircuitItemStackHandler circuitInventory;
    @Nullable
    private final ItemFilterContainer itemFilter;
    @Nullable
    private final FluidFilterContainer fluidFilter;
    private boolean workingEnabled = true;

    public MetaTileEntityDualHatch(ResourceLocation id, int tier, boolean isExportHatch) {
        super(id, tier);
        if (tier < GTValues.LuV || tier > GTValues.OpV)
            throw new IllegalArgumentException("Combined hatch tier: " + tier);
        this.isExportHatch = isExportHatch;
        this.rows = tier - 4;
        this.tankCapacity = Math.multiplyExact(16_000, 1 << (tier - GTValues.LuV));
        this.itemFilter = isExportHatch ? new ItemFilterContainer(this::onFilterChanged) : null;
        this.fluidFilter = isExportHatch ? new FluidFilterContainer(this::onFilterChanged) : null;
        this.physicalItems = isExportHatch ?
                new FilteredNotifiableItemStackHandler(this, Math.multiplyExact(rows, rows), this, itemFilter::test) :
                new NotifiableItemStackHandler(this, Math.multiplyExact(rows, rows), this, false);
        FluidTank[] tanks = new FluidTank[rows];
        IFilter<FluidStack> predicate = new IFilter<>() {

            @Override
            public boolean test(@NotNull FluidStack stack) {
                return fluidFilter == null || fluidFilter.test(stack);
            }

            @Override
            public int getPriority() {
                return fluidFilter != null && fluidFilter.isBlacklistFilter() ?
                        IFilter.blacklistLikePriority() : IFilter.whitelistLikePriority();
            }
        };
        for (int i = 0; i < rows; i++) {
            tanks[i] = isExportHatch ? new FilteredNotifiableFluidTank(tankCapacity, this,
                    () -> fluidFilter.hasFilter() ? predicate : null) :
                    new NotifiableFluidTank(tankCapacity, this, false);
        }
        this.physicalFluids = new FluidTankList(false, tanks);
        this.circuitInventory = isExportHatch ? null : new GhostCircuitItemStackHandler(this);
        if (circuitInventory != null) circuitInventory.addNotifiableMetaTileEntity(this);
        this.recipeHandler = new DualHandler(circuitInventory == null ? physicalItems :
                new ItemHandlerList(Arrays.asList(physicalItems, circuitInventory)), physicalFluids, isExportHatch);
        initializeInventory();
    }

    @Override
    protected void initializeInventory() {
        // MetaTileEntity calls this before this subclass's dimensions and stores have been initialized.
        if (physicalItems != null && physicalFluids != null) super.initializeInventory();
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return isExportHatch ? new GTItemStackHandler(this, 0) : physicalItems;
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return isExportHatch ? physicalItems : new GTItemStackHandler(this, 0);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return isExportHatch ? new FluidTankList(false) : physicalFluids;
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return isExportHatch ? physicalFluids : new FluidTankList(false);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
        return new MetaTileEntityDualHatch(metaTileEntityId, getTier(), isExportHatch);
    }

    public DualHandler getRecipeHandler() {
        return recipeHandler;
    }

    @Nullable
    public ItemFilterContainer getItemFilter() {
        return itemFilter;
    }

    @Nullable
    public FluidFilterContainer getFluidFilter() {
        return fluidFilter;
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return isExportHatch ? Arrays.asList(MultiblockAbility.EXPORT_ITEMS, MultiblockAbility.EXPORT_FLUIDS) :
                Arrays.asList(MultiblockAbility.IMPORT_ITEMS, MultiblockAbility.IMPORT_FLUIDS);
    }

    @Override
    public boolean requiresAllAbilityLimits() {
        return true;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances instances) {
        if (instances.isKey(isExportHatch ? MultiblockAbility.EXPORT_ITEMS : MultiblockAbility.IMPORT_ITEMS)) {
            instances.add(recipeHandler);
        } else if (instances.isKey(isExportHatch ? MultiblockAbility.EXPORT_FLUIDS : MultiblockAbility.IMPORT_FLUIDS)) {
            instances.addAll(physicalFluids.getFluidTanks());
        }
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controller) {
        super.addToMultiBlock(controller);
        recipeHandler.addNotifiableMetaTileEntity(controller);
        recipeHandler.onContentsChanged();
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controller) {
        recipeHandler.removeNotifiableMetaTileEntity(controller);
        super.removeFromMultiBlock(controller);
    }

    @Override
    public void addNotifiedInput(Object input) {
        super.addNotifiedInput(input);
        onPhysicalContentsChanged();
    }

    @Override
    public void addNotifiedOutput(Object output) {
        super.addNotifiedOutput(output);
        onPhysicalContentsChanged();
    }

    private void onPhysicalContentsChanged() {
        markDirty();
        if (recipeHandler != null) recipeHandler.onContentsChanged();
    }

    private void onFilterChanged() {
        onPhysicalContentsChanged();
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return circuitInventory != null;
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (circuitInventory != null && circuitInventory.getCircuitValue() != config) {
            circuitInventory.setCircuitValue(config);
            markDirty();
        }
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && workingEnabled && getOffsetTimer() % 5 == 0) {
            if (isExportHatch) {
                pushItemsIntoNearbyHandlers(getFrontFacing());
                pushFluidsIntoNearbyHandlers(getFrontFacing());
            } else {
                pullItemsFromNearbyHandlers(getFrontFacing());
                pullFluidsFromNearbyHandlers(getFrontFacing());
            }
        }
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean enabled) {
        if (workingEnabled == enabled) return;
        this.workingEnabled = enabled;
        markDirty();
        if (getWorld() != null && !getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(enabled));
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("workingEnabled", workingEnabled);
        if (circuitInventory != null) circuitInventory.write(data);
        if (itemFilter != null) data.setTag("ItemFilter", itemFilter.serializeNBT());
        if (fluidFilter != null) data.setTag("FluidFilter", fluidFilter.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.workingEnabled = !data.hasKey("workingEnabled") || data.getBoolean("workingEnabled");
        if (circuitInventory != null) circuitInventory.read(data);
        if (itemFilter != null) itemFilter.deserializeNBT(data.getCompoundTag("ItemFilter"));
        if (fluidFilter != null) fluidFilter.deserializeNBT(data.getCompoundTag("FluidFilter"));
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
        if (circuitInventory != null) buf.writeInt(circuitInventory.getCircuitValue());
        for (var tank : physicalFluids) NetworkUtils.writeFluidStack(buf, tank.getFluid());
        if (itemFilter != null) itemFilter.writeInitialSyncData(buf);
        if (fluidFilter != null) fluidFilter.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
        if (circuitInventory != null) circuitInventory.setCircuitValue(buf.readInt());
        for (var tank : physicalFluids) ((FluidTank) tank.getDelegate()).setFluid(NetworkUtils.readFluidStack(buf));
        if (itemFilter != null) itemFilter.readInitialSyncData(buf);
        if (fluidFilter != null) fluidFilter.readInitialSyncData(buf);
    }

    @Override
    public void receiveCustomData(int id, PacketBuffer buf) {
        super.receiveCustomData(id, buf);
        if (id == GregtechDataCodes.WORKING_ENABLED) this.workingEnabled = buf.readBoolean();
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> drops) {
        super.clearMachineInventory(drops);
        if (itemFilter != null) clearInventory(drops, itemFilter);
        if (fluidFilter != null) clearInventory(drops, fluidFilter);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState state, Matrix4 transform, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(state, transform, pipeline);
        if (shouldRenderOverlay()) {
            (isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY)
                    .renderSided(getFrontFacing(), state, transform, pipeline);
            (isExportHatch ? Textures.ITEM_HATCH_OUTPUT_OVERLAY : Textures.ITEM_HATCH_INPUT_OVERLAY)
                    .renderSided(getFrontFacing(), state, transform, pipeline);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        manager.registerSlotGroup("dual_items", rows);
        int width = Math.max(198, (rows + 1) * 18 + 21);
        int filterTop = 21 + rows * 18;
        int height = filterTop + (isExportHatch ? 42 : 0) + 94;
        int left = (width - (rows + 1) * 18 - 4) / 2;
        BooleanSyncValue working = new BooleanSyncValue(this::isWorkingEnabled, this::setWorkingEnabled);
        ModularPanel panel = GTGuis.createPanel(this, width, height)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new Grid().pos(left, 18).coverChildren().minElementMargin(0, 0).minColWidth(18).minRowHeight(18)
                        .mapTo(rows, physicalItems.getSlots(), index -> new ItemSlot()
                                .slot(SyncHandlers.itemSlot(physicalItems, index).slotGroup("dual_items")
                                        .changeListener((stack, amountOnly, client, init) -> {
                                            if (amountOnly)
                                                ((GTItemStackHandler) physicalItems).onContentsChanged(index);
                                        }).accessibility(!isExportHatch, true))))
                .child(new Grid().pos(left + rows * 18 + 4, 18).coverChildren().minElementMargin(0, 0)
                        .mapTo(1, rows, index -> new GTFluidSlot()
                                .syncHandler(GTFluidSlot.sync(physicalFluids.getTankAt(index))
                                        .accessibility(true, !isExportHatch))))
                .child(new ToggleButton().pos(width - 25, height - 43).value(working)
                        .overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                        .tooltip(t -> t.addLine(IKey.lang("gregtech.machine.dual_hatch.auto_transfer"))))
                .child(GTGuiTextures.getLogo(getUITheme()).asWidget().size(17).pos(width - 25, height - 24));
        if (circuitInventory != null) {
            panel.child(new GhostCircuitSlotWidget().pos(width - 25, height - 63).slot(circuitInventory, 0)
                    .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY));
        }
        if (itemFilter != null) {
            panel.child(Flow.column().pos(7, filterTop).width(width - 14).coverChildrenHeight()
                    .child(itemFilter.initUI(data, manager, "dual_item_filter"))
                    .child(fluidFilter.initUI(data, manager, "dual_fluid_filter")));
        }
        return panel;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format(isExportHatch ? "gregtech.machine.dual_hatch.export.tooltip" :
                "gregtech.machine.dual_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", physicalItems.getSlots()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", rows, tankCapacity));
        if (isExportHatch) tooltip.add(I18n.format("gregtech.machine.output_filter.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }
}

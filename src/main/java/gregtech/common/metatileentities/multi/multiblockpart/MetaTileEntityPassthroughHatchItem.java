package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MetaTileEntityPassthroughHatchItem extends MetaTileEntityPassthroughHatch<IItemHandler> {

    private ItemStackHandler itemStackHandler;

    public MetaTileEntityPassthroughHatchItem(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, "Inventory", "gregtech.gui.item_passthrough");
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityPassthroughHatchItem(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        itemInventory = itemStackHandler = new GTItemStackHandler(this, getInventorySize());
        importHandler = new ItemHandlerProxy(itemStackHandler, new GTItemStackHandler(this, 0));
        exportHandler = new ItemHandlerProxy(new GTItemStackHandler(this, 0), itemStackHandler);
    }

    private int getInventorySize() {
        int sizeRoot = 1 + Math.min(9, getTier());
        return sizeRoot * sizeRoot;
    }

    @Override
    protected void transferContents() {
        pushItemsIntoNearbyHandlers(getFrontFacing().getOpposite()); // outputs to back
        pullItemsFromNearbyHandlers(getFrontFacing()); // inputs from front
    }

    @Override
    protected Capability<IItemHandler> getHandlerCapability() {
        return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
    }

    @Override
    protected SimpleOverlayRenderer getHatchOverlay(boolean input) {
        return input ? Textures.ITEM_HATCH_INPUT_OVERLAY : Textures.ITEM_HATCH_OUTPUT_OVERLAY;
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, getInventorySize(), getController(), true);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, getInventorySize(), getController(), false);
    }

    @Override
    protected int getRowSize() {
        return (int) Math.sqrt(getInventorySize());
    }

    @Override
    protected void registerSlotGroups(PanelSyncManager syncManager, int rowSize) {
        syncManager.registerSlotGroup("item_inv", rowSize);
    }

    @Override
    protected IWidget createSlotWidget(int index) {
        return new ItemSlot()
                .slot(SyncHandlers.itemSlot(itemStackHandler, index)
                        .slotGroup("item_inv")
                        .accessibility(true, true));
    }

    @Override
    protected int getWorkingButtonTop() {
        return 18 * 4;
    }

    @Override
    protected IDrawable getWorkingButtonOverlay() {
        return GTGuiTextures.BUTTON_ITEM_OUTPUT;
    }

    @Override
    protected NBTTagCompound serializeContents() {
        return itemStackHandler.serializeNBT();
    }

    @Override
    protected void deserializeContents(NBTTagCompound tag) {
        this.itemStackHandler.deserializeNBT(tag);
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        clearInventory(itemBuffer, itemStackHandler);
    }

    @Override
    protected String getCapacityTooltip() {
        return I18n.format("gregtech.universal.tooltip.item_storage_capacity", getInventorySize());
    }

    @NotNull
    @Override
    public Class<IItemHandlerModifiable> getPassthroughType() {
        return IItemHandlerModifiable.class;
    }
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.storage.data.IAEItemStack;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MetaTileEntityMEInputBus extends MetaTileEntityMEInputPart<IAEItemStack, ExportOnlyAEItemSlot>
                                      implements IMultiblockAbilityPart<IItemHandlerModifiable>,
                                      IGhostSlotConfigurable {

    public final static String ITEM_BUFFER_TAG = "ItemSlots";
    protected ExportOnlyAEItemList aeItemHandler;
    protected GhostCircuitItemStackHandler circuitInventory;
    protected NotifiableItemStackHandler extraSlotInventory;
    private ItemHandlerList actualImportItems;

    public MetaTileEntityMEInputBus(ResourceLocation metaTileEntityId) {
        this(metaTileEntityId, GTValues.EV);
    }

    protected MetaTileEntityMEInputBus(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, MEStackType.ITEMS, ITEM_BUFFER_TAG, "stack", "MEInputBus",
                "gregtech.machine.me.item_import.data_stick.name");
    }

    protected ExportOnlyAEItemList getAEItemHandler() {
        if (aeItemHandler == null) {
            aeItemHandler = new ExportOnlyAEItemList(this, CONFIG_SIZE, this.getController());
        }
        return aeItemHandler;
    }

    @Override
    protected ExportOnlyAEItemSlot[] getAESlots() {
        return getAEItemHandler().getInventory();
    }

    @Override
    protected void takeStock(ExportOnlyAEItemSlot slot, int amount) {
        slot.extractItem(0, amount, false);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.aeItemHandler = getAEItemHandler();
        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.extraSlotInventory = new NotifiableItemStackHandler(this, 1, this, false);
        this.extraSlotInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                Arrays.asList(this.aeItemHandler, this.circuitInventory, this.extraSlotInventory));
        this.importItems = this.actualImportItems;
    }

    public IItemHandlerModifiable getImportItems() {
        return this.actualImportItems;
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        // what the network does not take back (it is offline or full) drops as items instead of vanishing
        for (IAEItemStack remainder : flushInventory()) {
            long left = remainder.getStackSize();
            while (left > 0) {
                ItemStack stack = remainder.createItemStack();
                if (stack.isEmpty()) break;
                int count = (int) Math.min(left, stack.getMaxStackSize());
                stack.setCount(count);
                itemBuffer.add(stack);
                left -= count;
            }
        }
    }

    @Override
    public void onRemoval() {
        // a broken bus was already emptied by clearMachineInventory
        flushInventory();
        super.onRemoval();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEInputBus(metaTileEntityId);
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        for (IItemHandler handler : this.actualImportItems.getBackingHandlers()) {
            if (handler instanceof INotifiableHandler notifiable) {
                notifiable.addNotifiableMetaTileEntity(controllerBase);
                notifiable.addToNotifiedList(this, handler, false);
            }
        }
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        super.removeFromMultiBlock(controllerBase);
        for (IItemHandler handler : this.actualImportItems.getBackingHandlers()) {
            if (handler instanceof INotifiableHandler notifiable) {
                notifiable.removeNotifiableMetaTileEntity(controllerBase);
            }
        }
    }

    @Override
    MEConfigSyncHandler<IAEItemStack, ItemStack> createConfigSync() {
        ExportOnlyAEItemList handler = getAEItemHandler();
        return new MEConfigSyncHandler<>(MEStackType.ITEMS, handler.getInventory(), handler.isStocking(),
                handler::isAutoPull, stack -> handler.hasStackInConfig(stack, true), this::markDirty);
    }

    @Override
    protected void addSideWidgets(ModularPanel panel, PanelSyncManager syncManager) {
        panel.child(GTGuiTextures.ARROW_DOUBLE.asWidget().pos(7 + 18 * 4, 25 + 18).size(18))
                .child(new ItemSlot()
                        .pos(7 + 18 * 4, 25 + 18 * 2)
                        .slot(new ModularSlot(extraSlotInventory, 0).singletonSlotGroup())
                        .background(GTGuiTextures.SLOT)
                        .addTooltipLine(IKey.lang("gregtech.gui.me_bus.extra_slot")))
                .child(new GhostCircuitSlotWidget()
                        .slot(circuitInventory, 0)
                        .pos(7 + 18 * 4, 25 + 18 * 3));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.circuitInventory.write(data);
        // Extra slot inventory
        GTUtility.writeItems(this.extraSlotInventory, "ExtraInventory", data);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.circuitInventory.read(data);
        GTUtility.readItems(this.extraSlotInventory, "ExtraInventory", data);
        this.importItems = createImportItemHandler();
    }

    @Override
    protected SimpleOverlayRenderer getFrontOverlay(boolean online) {
        return online ? Textures.ME_INPUT_BUS_ACTIVE : Textures.ME_INPUT_BUS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.item_bus.import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me_import_item_hatch.configs.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.copy_paste.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this.actualImportItems);
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (this.circuitInventory.getCircuitValue() == config) {
            return;
        }
        this.circuitInventory.setCircuitValue(config);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    protected NBTTagCompound writeConfigToTag() {
        NBTTagCompound tag = super.writeConfigToTag();
        tag.setByte("GhostCircuit", (byte) this.circuitInventory.getCircuitValue());
        return tag;
    }

    @Override
    protected void readConfigFromTag(NBTTagCompound tag) {
        super.readConfigFromTag(tag);
        if (tag.hasKey("GhostCircuit")) {
            this.setGhostCircuitConfig(tag.getByte("GhostCircuit"));
        }
    }
}

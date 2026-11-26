package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.Widget;
import gregtech.api.gui.widgets.GhostCircuitSlotWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.gui.widget.appeng.AEItemConfigWidget;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.storage.data.IAEItemStack;
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
    protected Widget createConfigWidget(int x, int y) {
        return new AEItemConfigWidget(x, y, this.getAEItemHandler());
    }

    @Override
    protected void addSideWidgets(ModularUI.Builder builder) {
        // Ghost circuit slot
        SlotWidget circuitSlot = new GhostCircuitSlotWidget(circuitInventory, 0, 7 + 18 * 4, 25 + 18 * 3)
                .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.INT_CIRCUIT_OVERLAY);
        builder.widget(circuitSlot.setConsumer(w -> {
            String configString;
            if (circuitInventory == null ||
                    circuitInventory.getCircuitValue() == GhostCircuitItemStackHandler.NO_CONFIG) {
                configString = new TextComponentTranslation("gregtech.gui.configurator_slot.no_value")
                        .getFormattedText();
            } else {
                configString = String.valueOf(circuitInventory.getCircuitValue());
            }

            w.setTooltipText("gregtech.gui.configurator_slot.tooltip", configString);
        }));

        // Extra slot
        builder.widget(new SlotWidget(extraSlotInventory, 0, 7 + 18 * 4, 25 + 18 * 2)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setTooltipText("gregtech.gui.me_bus.extra_slot"));

        // Arrow image
        builder.image(7 + 18 * 4, 25 + 18, 18, 18, GuiTextures.ARROW_DOUBLE);
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

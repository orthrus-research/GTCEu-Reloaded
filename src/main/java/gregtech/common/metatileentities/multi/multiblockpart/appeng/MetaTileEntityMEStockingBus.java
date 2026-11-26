package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import appeng.api.storage.data.IAEItemStack;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** An input bus that shows what the network holds instead of buffering it; see {@link MEStockingLogic}. */
public class MetaTileEntityMEStockingBus extends MetaTileEntityMEInputBus {

    private final MEStockingLogic<IAEItemStack, ItemStack> stocking = new MEStockingLogic<>(this,
            MEStackType.ITEMS, this::testConfiguredInOtherBus);

    public MetaTileEntityMEStockingBus(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.IV);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEStockingBus(metaTileEntityId);
    }

    @Override
    protected ExportOnlyAEStockingItemList getAEItemHandler() {
        if (this.aeItemHandler == null) {
            this.aeItemHandler = new ExportOnlyAEStockingItemList(this, CONFIG_SIZE, getController());
        }
        return (ExportOnlyAEStockingItemList) this.aeItemHandler;
    }

    @Override
    protected boolean shouldUpdateME() {
        return stocking.shouldUpdate();
    }

    @Override
    public void update() {
        super.update();
        stocking.update();
    }

    @Override
    protected void onConfigPasted() {
        stocking.onConfigPasted();
    }

    @Override
    protected void syncME() {
        stocking.sync();
    }

    @Override
    protected @NotNull List<IAEItemStack> flushInventory() {
        // nothing to send back to the network
        return Collections.emptyList();
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        stocking.attach();
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        stocking.detach();
        super.removeFromMultiBlock(controllerBase);
    }

    @Override
    public void onDistinctChange(boolean newValue) {
        super.onDistinctChange(newValue);
        if (!getWorld().isRemote && !newValue) {
            // Ensure that our configured items won't match any other buses in the multiblock.
            // Needed since we allow duplicates in distinct mode on, but not off
            stocking.validateConfig();
        }
    }

    /**
     * @return True if the passed stack is found as a configuration in any other stocking buses on the multiblock.
     */
    private boolean testConfiguredInOtherBus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        MultiblockControllerBase controller = getController();
        if (controller == null) return false;

        // In distinct mode, we don't need to check other buses since only one bus can run a recipe at a time.
        if (!(controller instanceof RecipeMapMultiblockController rmmc) || !rmmc.isDistinct()) {
            // Otherwise, we need to test for if the item is configured
            // in any stocking bus in the multi (besides ourselves).
            var abilityList = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            for (var ability : abilityList) {
                if (ability instanceof ExportOnlyAEStockingItemList aeList) {
                    // We don't need to check for ourselves, as this case is handled elsewhere.
                    if (aeList == this.aeItemHandler) continue;
                    if (aeList.hasStackInConfig(stack, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        stocking.receiveCustomData(dataId, buf);
    }

    @Override
    protected int getUIExtraHeight() {
        return MEStockingConfig.UI_EXTRA_HEIGHT;
    }

    @Override
    protected ModularUI.Builder createUITemplate(EntityPlayer player) {
        ModularUI.Builder builder = super.createUITemplate(player);
        stocking.addWidgets(builder, false);
        return builder;
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        return stocking.onScrewdriverClick(playerIn);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        stocking.writeToNBT(data);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        stocking.readFromNBT(data);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        stocking.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        stocking.receiveInitialSyncData(buf);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.item_bus.import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.stocking_item.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me_import_item_hatch.configs.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.copy_paste.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.stocking_item.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    protected NBTTagCompound writeConfigToTag() {
        NBTTagCompound tag = stocking.writeConfigToTag(super::writeConfigToTag);
        if (stocking.isAutoPull()) {
            // the configured slots are not copied in auto-pull, but the ghost circuit still is
            tag.setByte("GhostCircuit", (byte) this.circuitInventory.getCircuitValue());
        }
        return tag;
    }

    @Override
    protected void readConfigFromTag(NBTTagCompound tag) {
        if (!stocking.readConfigFromTag(tag)) {
            super.readConfigFromTag(tag);
        } else if (tag.hasKey("GhostCircuit")) {
            this.setGhostCircuitConfig(tag.getByte("GhostCircuit"));
        }
    }

    private static class ExportOnlyAEStockingItemSlot extends ExportOnlyAEItemSlot {

        private final MetaTileEntityMEStockingBus holder;

        public ExportOnlyAEStockingItemSlot(IAEItemStack config, IAEItemStack stock,
                                            MetaTileEntityMEStockingBus holder) {
            super(config, stock);
            this.holder = holder;
        }

        public ExportOnlyAEStockingItemSlot(MetaTileEntityMEStockingBus holder) {
            super();
            this.holder = holder;
        }

        @Override
        public ExportOnlyAEStockingItemSlot copy() {
            return new ExportOnlyAEStockingItemSlot(
                    this.config == null ? null : this.config.copy(),
                    this.stock == null ? null : this.stock.copy(),
                    this.holder);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || this.stock == null || this.config == null) return ItemStack.EMPTY;
            // Extract the items from the real net to either validate (simulate)
            // or extract (modulate) when this is called
            IAEItemStack result = holder.stocking.extract(this.config, amount, simulate);
            if (result == null) return ItemStack.EMPTY;

            int extracted = (int) Math.min(result.getStackSize(), amount);
            this.stock.decStackSize(extracted); // may as well update the display here
            if (this.trigger != null) {
                this.trigger.accept(0);
            }
            if (extracted == 0) return ItemStack.EMPTY;
            ItemStack resultStack = this.config.createItemStack();
            resultStack.setCount(extracted);
            return resultStack;
        }
    }

    private static class ExportOnlyAEStockingItemList extends ExportOnlyAEItemList {

        private final MetaTileEntityMEStockingBus holder;

        public ExportOnlyAEStockingItemList(MetaTileEntityMEStockingBus holder, int slots,
                                            MetaTileEntity entityToNotify) {
            super(holder, slots, entityToNotify);
            this.holder = holder;
        }

        @Override
        protected void createInventory(MetaTileEntity holder) {
            if (!(holder instanceof MetaTileEntityMEStockingBus stocking)) {
                throw new IllegalArgumentException("Cannot create Stocking Item List for nonstocking MetaTileEntity!");
            }
            this.inventory = new ExportOnlyAEStockingItemSlot[size];
            for (int i = 0; i < size; i++) {
                this.inventory[i] = new ExportOnlyAEStockingItemSlot(stocking);
            }
            for (ExportOnlyAEItemSlot slot : this.inventory) {
                slot.setTrigger(this::onContentsChanged);
            }
        }

        @Override
        public ExportOnlyAEStockingItemSlot[] getInventory() {
            return (ExportOnlyAEStockingItemSlot[]) super.getInventory();
        }

        @Override
        public boolean isStocking() {
            return true;
        }

        @Override
        public boolean isAutoPull() {
            return holder.stocking.isAutoPull();
        }

        @Override
        public boolean hasStackInConfig(ItemStack stack, boolean checkExternal) {
            boolean inThisBus = super.hasStackInConfig(stack, false);
            if (inThisBus) return true;
            if (checkExternal) {
                return holder.testConfiguredInOtherBus(stack);
            }
            return false;
        }
    }
}

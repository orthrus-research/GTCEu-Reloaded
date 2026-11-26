package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** An input hatch that shows what the network holds instead of buffering it, as the stocking bus does. */
public class MetaTileEntityMEStockingHatch extends MetaTileEntityMEInputHatch {

    private final MEStockingLogic<IAEFluidStack, FluidStack> stocking = new MEStockingLogic<>(this,
            MEStackType.FLUIDS, this::testConfiguredInOtherHatch);

    public MetaTileEntityMEStockingHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.IV);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEStockingHatch(metaTileEntityId);
    }

    @Override
    protected ExportOnlyAEStockingFluidList getAEFluidHandler() {
        if (this.aeFluidHandler == null) {
            this.aeFluidHandler = new ExportOnlyAEStockingFluidList(this, CONFIG_SIZE, getController());
        }
        return (ExportOnlyAEStockingFluidList) this.aeFluidHandler;
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
    protected @NotNull List<IAEFluidStack> flushInventory() {
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

    private boolean testConfiguredInOtherHatch(FluidStack stack) {
        if (stack == null) return false;
        MultiblockControllerBase controller = getController();
        if (controller == null) return false;

        var abilityList = controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS);
        for (var ability : abilityList) {
            if (ability instanceof ExportOnlyAEStockingFluidSlot aeSlot) {
                if (aeSlot.getConfig() == null) continue;
                if (getAEFluidHandler().ownsSlot(aeSlot)) continue;
                if (aeSlot.getConfig().getFluid().equals(stack.getFluid())) {
                    return true;
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
    void addExtraWidgets(ModularPanel panel, PanelSyncManager syncManager,
                         MEConfigSyncHandler<IAEFluidStack, ?> config) {
        // a stocking part has no amounts to set; its settings take the place below the slots
        stocking.addWidgets(panel, syncManager, true);
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
        tooltip.add(I18n.format("gregtech.machine.fluid_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.stocking_fluid.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me_import_fluid_hatch.configs.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.copy_paste.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.stocking_fluid.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    protected NBTTagCompound writeConfigToTag() {
        return stocking.writeConfigToTag(super::writeConfigToTag);
    }

    @Override
    protected void readConfigFromTag(NBTTagCompound tag) {
        if (!stocking.readConfigFromTag(tag)) {
            super.readConfigFromTag(tag);
        }
    }

    private static class ExportOnlyAEStockingFluidSlot extends ExportOnlyAEFluidSlot {

        public ExportOnlyAEStockingFluidSlot(MetaTileEntityMEStockingHatch holder, IAEFluidStack config,
                                             IAEFluidStack stock, MetaTileEntity entityToNotify) {
            super(holder, config, stock, entityToNotify);
        }

        public ExportOnlyAEStockingFluidSlot(MetaTileEntityMEStockingHatch holder, MetaTileEntity entityToNotify) {
            super(holder, entityToNotify);
        }

        @Override
        protected MetaTileEntityMEStockingHatch getHolder() {
            return (MetaTileEntityMEStockingHatch) super.getHolder();
        }

        @Override
        public ExportOnlyAEFluidSlot copy() {
            return new ExportOnlyAEStockingFluidSlot(
                    this.getHolder(),
                    this.config == null ? null : this.config.copy(),
                    this.stock == null ? null : this.stock.copy(),
                    null);
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            if (this.stock == null || this.config == null) return null;
            IAEFluidStack result = getHolder().stocking.extract(this.config, maxDrain, !doDrain);
            if (result == null) return null;

            int extracted = (int) Math.min(result.getStackSize(), maxDrain);
            this.stock.decStackSize(extracted);
            trigger();
            if (extracted == 0) return null;
            FluidStack resultStack = this.config.getFluidStack();
            resultStack.amount = extracted;
            return resultStack;
        }
    }

    private static class ExportOnlyAEStockingFluidList extends ExportOnlyAEFluidList {

        private final MetaTileEntityMEStockingHatch holder;

        public ExportOnlyAEStockingFluidList(MetaTileEntityMEStockingHatch holder, int slots,
                                             MetaTileEntity entityToNotify) {
            super(holder, slots, entityToNotify);
            this.holder = holder;
        }

        @Override
        protected void createInventory(MetaTileEntity holder, MetaTileEntity entityToNotify) {
            if (!(holder instanceof MetaTileEntityMEStockingHatch stocking)) {
                throw new IllegalArgumentException("Cannot create Stocking Fluid List for nonstocking MetaTileEntity!");
            }
            this.inventory = new ExportOnlyAEStockingFluidSlot[size];
            for (int i = 0; i < size; i++) {
                this.inventory[i] = new ExportOnlyAEStockingFluidSlot(stocking, entityToNotify);
            }
        }

        @Override
        public ExportOnlyAEStockingFluidSlot[] getInventory() {
            return (ExportOnlyAEStockingFluidSlot[]) super.getInventory();
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
        public boolean hasStackInConfig(FluidStack stack, boolean checkExternal) {
            boolean inThisHatch = super.hasStackInConfig(stack, false);
            if (inThisHatch) return true;
            if (checkExternal) {
                return holder.testConfiguredInOtherHatch(stack);
            }
            return false;
        }
    }
}

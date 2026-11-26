package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.Widget;
import gregtech.api.gui.widgets.ImageWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.gui.widget.appeng.AEFluidConfigWidget;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidSlot;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidTank;

import appeng.api.storage.data.IAEFluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MetaTileEntityMEInputHatch extends MetaTileEntityMEInputPart<IAEFluidStack, ExportOnlyAEFluidSlot>
                                        implements IMultiblockAbilityPart<IFluidTank> {

    public final static String FLUID_BUFFER_TAG = "FluidTanks";
    private static final String LEFTOVER_FLUIDS_TAG = "LeftoverFluids";
    /** What the network did not take back when this hatch was removed; written into the dropped item. */
    private List<IAEFluidStack> removalRemainders = Collections.emptyList();
    protected ExportOnlyAEFluidList aeFluidHandler;

    public MetaTileEntityMEInputHatch(ResourceLocation metaTileEntityId) {
        this(metaTileEntityId, GTValues.EV);
    }

    protected MetaTileEntityMEInputHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, MEStackType.FLUIDS, FLUID_BUFFER_TAG, "tank", "MEInputHatch",
                "gregtech.machine.me.fluid_import.data_stick.name");
    }

    protected ExportOnlyAEFluidList getAEFluidHandler() {
        if (aeFluidHandler == null) {
            aeFluidHandler = new ExportOnlyAEFluidList(this, CONFIG_SIZE, this.getController());
        }
        return aeFluidHandler;
    }

    @Override
    protected ExportOnlyAEFluidSlot[] getAESlots() {
        return getAEFluidHandler().getInventory();
    }

    @Override
    protected void takeStock(ExportOnlyAEFluidSlot slot, int amount) {
        slot.drain(amount, true);
    }

    @Override
    protected void initializeInventory() {
        getAEFluidHandler(); // initialize it
        super.initializeInventory();
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false, getAEFluidHandler().getInventory());
    }

    @Override
    protected boolean shouldUpdateME() {
        // unlike the bus, the hatch reads its network status only on the ticks it syncs
        return this.isWorkingEnabled() && this.shouldSyncME() && updateMEStatus();
    }

    @Override
    public void onRemoval() {
        // fluids cannot drop; what the network does not take back is kept in the dropped hatch
        this.removalRemainders = flushInventory();
        super.onRemoval();
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        if (this.removalRemainders.isEmpty()) return;
        NBTTagList fluids = new NBTTagList();
        for (IAEFluidStack fluid : this.removalRemainders) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.writeToNBT(fluidTag);
            fluids.appendTag(fluidTag);
        }
        itemStack.setTag(LEFTOVER_FLUIDS_TAG, fluids);
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (!itemStack.hasKey(LEFTOVER_FLUIDS_TAG, 9)) return;
        // held without a config, the fluids go back to the network once the hatch is connected
        NBTTagList fluids = itemStack.getTagList(LEFTOVER_FLUIDS_TAG, 10);
        ExportOnlyAEFluidSlot[] tanks = this.getAEFluidHandler().getInventory();
        for (int i = 0; i < fluids.tagCount() && i < tanks.length; i++) {
            WrappedFluidStack fluid = WrappedFluidStack.fromNBT(fluids.getCompoundTagAt(i));
            if (fluid != null) tanks[i].setStock(fluid);
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEInputHatch(this.metaTileEntityId);
    }

    @Override
    protected Widget createConfigWidget(int x, int y) {
        return new AEFluidConfigWidget(x, y, this.getAEFluidHandler());
    }

    @Override
    protected void addSideWidgets(ModularUI.Builder builder) {
        // Arrow image
        builder.image(7 + 18 * 4, 25 + 18, 18, 18, GuiTextures.ARROW_DOUBLE);

        // GT Logo, cause there's some free real estate
        builder.widget(new ImageWidget(7 + 18 * 4, 25 + 18 * 3, 17, 17,
                GTValues.XMAS.get() ? GuiTextures.GREGTECH_LOGO_XMAS : GuiTextures.GREGTECH_LOGO)
                .setIgnoreColor(true));
    }

    @Override
    protected SimpleOverlayRenderer getFrontOverlay(boolean online) {
        return online ? Textures.ME_INPUT_HATCH_ACTIVE : Textures.ME_INPUT_HATCH;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.fluid_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me_import_fluid_hatch.configs.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.copy_paste.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public MultiblockAbility<IFluidTank> getAbility() {
        return MultiblockAbility.IMPORT_FLUIDS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.addAll(Arrays.asList(this.getAEFluidHandler().getInventory()));
    }
}

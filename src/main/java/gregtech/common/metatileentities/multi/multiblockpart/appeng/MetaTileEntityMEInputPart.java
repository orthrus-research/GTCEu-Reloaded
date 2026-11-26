package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAESlot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An ME input bus or hatch: sixteen config slots kept filled from the network. This holds everything about them that
 * does not depend on whether they hold items or fluids; subclasses supply the slots and the widgets beside them.
 */
public abstract class MetaTileEntityMEInputPart<T extends IAEStack<T>, S extends ExportOnlyAESlot<T>>
                                               extends MetaTileEntityAEHostablePart<T>
                                               implements IDataStickIntractable {

    public final static String WORKING_TAG = "WorkingEnabled";
    protected final static int CONFIG_SIZE = 16;
    /** How many config slots make a row; the stock they hold shows beside them in rows as long. */
    private static final int ROW_LENGTH = 4;
    private final MEStackType<T, ?> stackType;
    private final String slotsTag;
    private final String slotTag;
    private final String dataStickTag;
    private final String dataStickName;
    private boolean workingEnabled = true;

    /**
     * @param slotsTag      the save key of the slot list
     * @param slotTag       the save key of each slot's contents within it
     * @param dataStickTag  the key a data stick copies the configuration under
     * @param dataStickName the name a data stick takes when it copies
     */
    MetaTileEntityMEInputPart(ResourceLocation metaTileEntityId, int tier, MEStackType<T, ?> stackType,
                              String slotsTag, String slotTag, String dataStickTag, String dataStickName) {
        super(metaTileEntityId, tier, false, stackType.getStorageChannel());
        this.stackType = stackType;
        this.slotsTag = slotsTag;
        this.slotTag = slotTag;
        this.dataStickTag = dataStickTag;
        this.dataStickName = dataStickName;
    }

    /** The config slots, in order. */
    protected abstract S[] getAESlots();

    /** Removes from a slot what the network took back. */
    protected abstract void takeStock(S slot, int amount);

    /** The screen's view of the config slots, with the edits this part allows. */
    abstract MEConfigSyncHandler<T, ?> createConfigSync();

    /** Adds the widgets in the column right of the config slots. */
    protected abstract void addSideWidgets(ModularPanel panel, PanelSyncManager syncManager);

    /** The front overlay, lit while the part is online. */
    protected abstract SimpleOverlayRenderer getFrontOverlay(boolean online);

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && shouldUpdateME()) {
            syncME();
        }
    }

    protected boolean shouldUpdateME() {
        return this.workingEnabled && updateMEStatus() && shouldSyncME();
    }

    protected void syncME() {
        IMEMonitor<T> monitor = getMonitor();
        if (monitor == null) return;

        for (S aeSlot : getAESlots()) {
            // Try to clear the wrong stack
            T exceed = aeSlot.exceedStack();
            if (exceed != null) {
                long total = exceed.getStackSize();
                T notInserted = monitor.injectItems(exceed, Actionable.MODULATE, this.getActionSource());
                if (notInserted != null && notInserted.getStackSize() > 0) {
                    takeStock(aeSlot, (int) (total - notInserted.getStackSize()));
                    continue;
                } else {
                    takeStock(aeSlot, (int) total);
                }
            }
            // Fill it
            T request = aeSlot.requestStack();
            if (request != null) {
                T extracted = monitor.extractItems(request, Actionable.MODULATE, this.getActionSource());
                if (extracted != null) {
                    aeSlot.addStack(extracted);
                }
            }
        }
    }

    /**
     * Returns the held stock to the network and empties the slots.
     *
     * @return what the network did not accept
     */
    @NotNull
    protected List<T> flushInventory() {
        IMEMonitor<T> monitor = getMonitor();
        List<T> remainders = new ArrayList<>();
        for (S aeSlot : getAESlots()) {
            T stock = stackType.unwrap(aeSlot.getStock());
            if (stock == null || stock.getStackSize() <= 0) continue;
            T left = monitor == null ? stock.copy() :
                    monitor.injectItems(stock.copy(), Actionable.MODULATE, this.getActionSource());
            if (left != null && left.getStackSize() > 0) remainders.add(left);
            aeSlot.setStock(null);
        }
        return remainders;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public final ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        MEConfigSyncHandler<T, ?> config = createConfigSync();
        BooleanSyncValue online = new BooleanSyncValue(() -> this.isOnline);
        guiSyncManager.syncValue("me_config", config);
        guiSyncManager.syncValue("me_online", online);

        ModularPanel panel = GTGuis.createPanel(this, 176, 18 + 18 * 4 + 94 + getUIExtraHeight())
                .child(IKey.lang(getMetaFullName()).asWidget().pos(10, 5))
                .child(IKey.dynamicKey(() -> IKey.lang(online.getBoolValue() ? "gregtech.gui.me_network.online" :
                        "gregtech.gui.me_network.offline")).asWidget().pos(10, 15));
        addConfigSlots(panel, config);
        addSideWidgets(panel, guiSyncManager);
        addExtraWidgets(panel, guiSyncManager, config);
        return panel.bindPlayerInventory();
    }

    private static <T extends IAEStack<T>, N> void addConfigSlots(ModularPanel panel,
                                                                  MEConfigSyncHandler<T, N> config) {
        for (int i = 0; i < config.getSize(); i++) {
            int index = i;
            int x = index % ROW_LENGTH * 18;
            int y = 25 + index / ROW_LENGTH * 18;
            panel.child(new MEConfigSlotWidget<>(config, index).pos(7 + x, y))
                    .child(new MEStackDisplayWidget<>(config.getStackType(), () -> config.getStock(index),
                            GTGuiTextures.SLOT_DARK).pos(7 + 18 * 5 + x, y));
        }
    }

    /** The height the screen adds below the config slots, for the widgets {@link #addExtraWidgets} adds. */
    protected int getUIExtraHeight() {
        return 18;
    }

    /** Adds the widgets below the config slots: the amount of the selected slot. */
    void addExtraWidgets(ModularPanel panel, PanelSyncManager syncManager, MEConfigSyncHandler<T, ?> config) {
        panel.child(IKey.lang("gregtech.gui.config_slot.amount").asWidget()
                .pos(7, 105)
                .setEnabledIf(widget -> config.getSelected() >= 0))
                .child(new TextFieldWidget()
                        .pos(50, 101).size(65, 14)
                        .background(GTGuiTextures.DISPLAY)
                        .setTextColor(Color.WHITE.main)
                        .setMaxLength(10)
                        .setNumbersLong(amount -> Math.max(1, Math.min(Integer.MAX_VALUE, amount)))
                        .value(new StringValue.Dynamic(() -> {
                            int selected = config.getSelected();
                            T stack = selected < 0 ? null : config.getConfig(selected);
                            return stack == null ? "0" : Long.toString(stack.getStackSize());
                        }, text -> {
                            int selected = config.getSelected();
                            try {
                                if (selected >= 0) config.setAmount(selected, Long.parseLong(text.trim()));
                            } catch (NumberFormatException ignored) {}
                        }))
                        .setEnabledIf(widget -> config.getSelected() >= 0));
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = this.getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
    }

    @Override
    public <C> C getCapability(Capability<C> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean(WORKING_TAG, this.workingEnabled);
        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < CONFIG_SIZE; i++) {
            S slot = getAESlots()[i];
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", i);
            slotTag.setTag(this.slotTag, slot.serializeNBT());
            slots.appendTag(slotTag);
        }
        data.setTag(slotsTag, slots);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(WORKING_TAG)) {
            this.workingEnabled = data.getBoolean(WORKING_TAG);
        }
        if (data.hasKey(slotsTag, 9)) {
            NBTTagList slots = (NBTTagList) data.getTag(slotsTag);
            for (NBTBase nbtBase : slots) {
                NBTTagCompound slotTag = (NBTTagCompound) nbtBase;
                S slot = getAESlots()[slotTag.getInteger("slot")];
                slot.deserializeNBT(slotTag.getCompoundTag(this.slotTag));
            }
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            getFrontOverlay(isOnline).renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public final void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(dataStickTag, writeConfigToTag());
        dataStick.setTagCompound(tag);
        dataStick.setTranslatableName(dataStickName);
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.me.import_copy_settings"), true);
    }

    protected NBTTagCompound writeConfigToTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound configStacks = new NBTTagCompound();
        tag.setTag("ConfigStacks", configStacks);
        for (int i = 0; i < CONFIG_SIZE; i++) {
            T config = getAESlots()[i].getConfig();
            if (config == null) {
                continue;
            }
            NBTTagCompound stackNbt = new NBTTagCompound();
            stackType.writeConfig(config, stackNbt);
            configStacks.setTag(Integer.toString(i), stackNbt);
        }
        return tag;
    }

    @Override
    public final boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null || !tag.hasKey(dataStickTag)) {
            return false;
        }
        readConfigFromTag(tag.getCompoundTag(dataStickTag));
        onConfigPasted();
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.me.import_paste_settings"), true);
        return true;
    }

    protected void onConfigPasted() {
        syncME();
    }

    protected void readConfigFromTag(NBTTagCompound tag) {
        if (tag.hasKey("ConfigStacks")) {
            NBTTagCompound configStacks = tag.getCompoundTag("ConfigStacks");
            for (int i = 0; i < CONFIG_SIZE; i++) {
                String key = Integer.toString(i);
                if (configStacks.hasKey(key)) {
                    NBTTagCompound configTag = configStacks.getCompoundTag(key);
                    getAESlots()[i].setConfig(stackType.readConfig(configTag));
                } else {
                    getAESlots()[i].setConfig(null);
                }
            }
        }
    }
}

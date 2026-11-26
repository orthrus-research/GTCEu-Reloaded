package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import java.util.ArrayList;
import java.util.List;

/**
 * An ME output bus or hatch: what a recipe outputs waits in an unbounded buffer until the network takes it. This
 * holds everything that does not depend on whether the buffer holds items or fluids.
 */
public abstract class MetaTileEntityMEOutputPart<T extends IAEStack<T>,
        B extends IItemList<T> & INBTSerializable<NBTTagList>> extends MetaTileEntityAEHostablePart<T> {

    public final static String WORKING_TAG = "WorkingEnabled";
    private final MEStackType<T, ?> stackType;
    private final String bufferTag;
    private boolean workingEnabled = true;
    private B internalBuffer;

    /**
     * @param bufferTag the key the buffer is saved under, in the world and in the dropped part
     */
    MetaTileEntityMEOutputPart(ResourceLocation metaTileEntityId, MEStackType<T, ?> stackType, String bufferTag) {
        super(metaTileEntityId, GTValues.EV, true, stackType.getStorageChannel());
        this.stackType = stackType;
        this.bufferTag = bufferTag;
    }

    protected abstract B createBuffer();

    /** Tells the multiblock that it may void this part's kind of output into the buffer. */
    protected abstract void enableInfiniteSink(MultiblockWithDisplayBase controller);

    /** The front overlay, lit while the part is online. */
    protected abstract SimpleOverlayRenderer getFrontOverlay(boolean online);

    protected final B getInternalBuffer() {
        return internalBuffer;
    }

    @Override
    protected void initializeInventory() {
        this.internalBuffer = createBuffer();
        super.initializeInventory();
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && this.workingEnabled && this.shouldSyncME() && this.updateMEStatus()) {
            if (this.internalBuffer.isEmpty()) return;

            IMEMonitor<T> monitor = getMonitor();
            if (monitor == null) return;

            returnBuffer(monitor);
        }
    }

    @Override
    public void onRemoval() {
        // what the network does not take back (it is offline or full) stays in the buffer and goes with the
        // dropped item, see writeItemStackData
        IMEMonitor<T> monitor = getMonitor();
        if (monitor != null) {
            returnBuffer(monitor);
        }
        super.onRemoval();
    }

    private void returnBuffer(IMEMonitor<T> monitor) {
        for (T stack : this.internalBuffer) {
            T notInserted = monitor.injectItems(stack.copy(), Actionable.MODULATE, getActionSource());
            if (notInserted != null && notInserted.getStackSize() > 0) {
                stack.setStackSize(notInserted.getStackSize());
            } else {
                stack.reset();
            }
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        if (!this.internalBuffer.isEmpty()) {
            itemStack.setTag(bufferTag, this.internalBuffer.serializeNBT());
        }
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(bufferTag, 9)) {
            this.internalBuffer.deserializeNBT((NBTTagList) itemStack.getTag(bufferTag));
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        MEBufferSyncHandler<T> waiting = new MEBufferSyncHandler<>(stackType, this.internalBuffer);
        BooleanSyncValue online = new BooleanSyncValue(() -> this.isOnline);
        guiSyncManager.syncValue("me_buffer", waiting);
        guiSyncManager.syncValue("me_online", online);
        return GTGuis.createPanel(this, 176, 18 + 18 * 4 + 94)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(10, 5))
                .child(IKey.dynamicKey(() -> IKey.lang(online.getBoolValue() ? "gregtech.gui.me_network.online" :
                        "gregtech.gui.me_network.offline")).asWidget().pos(10, 15))
                .child(IKey.lang("gregtech.gui.waiting_list").color(Color.WHITE.main).asWidget().pos(10, 25))
                .child(new MEBufferListWidget<>(stackType, waiting::getEntries, 3).pos(10, 35))
                .bindPlayerInventory();
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
        data.setTag(bufferTag, this.internalBuffer.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(WORKING_TAG)) {
            this.workingEnabled = data.getBoolean(WORKING_TAG);
        }
        if (data.hasKey(bufferTag, 9)) {
            this.internalBuffer.deserializeNBT((NBTTagList) data.getTag(bufferTag));
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
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        if (controllerBase instanceof MultiblockWithDisplayBase) {
            enableInfiniteSink((MultiblockWithDisplayBase) controllerBase);
        }
    }

    /** The handler a multiblock outputs into: it takes everything into the buffer and gives nothing back. */
    protected abstract static class InaccessibleInfiniteHandler<T extends IAEStack<T>> implements INotifiableHandler {

        private final IItemList<T> internalBuffer;
        private final List<MetaTileEntity> notifiableEntities = new ArrayList<>();
        private final MetaTileEntity holder;

        protected InaccessibleInfiniteHandler(MetaTileEntity holder, IItemList<T> internalBuffer,
                                              MetaTileEntity mte) {
            this.holder = holder;
            this.internalBuffer = internalBuffer;
            this.notifiableEntities.add(mte);
        }

        /** Adds an output to the buffer and marks the part for saving. */
        protected void insert(T stack) {
            this.internalBuffer.add(stack);
            this.holder.markDirty();
        }

        @Override
        public void addNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
            this.notifiableEntities.add(metaTileEntity);
        }

        @Override
        public void removeNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
            this.notifiableEntities.remove(metaTileEntity);
        }

        protected void trigger() {
            for (MetaTileEntity metaTileEntity : this.notifiableEntities) {
                if (metaTileEntity != null && metaTileEntity.isValid()) {
                    this.addToNotifiedList(metaTileEntity, this, true);
                }
            }
        }
    }
}

package gregtech.common.pipelike.beam.tile;

import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.api.util.TaskScheduler;
import gregtech.common.pipelike.beam.net.BeamNetHandler;
import gregtech.common.pipelike.beam.net.BeamPipeNet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

/**
 * A pipe of a beam family, laser or optical. It reaches the endpoint at the other end of its line through a net handler
 * per face, and lights up for a while each time a request passes. The family's pipe exposes its handlers as its
 * capabilities, with placeholders on the client, and decides which connections a line may have.
 */
public abstract class TileEntityBeamPipe<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>, NodeDataType,
        NetType extends BeamPipeNet<NodeDataType, ?>> extends TileEntityPipeBase<PipeType, NodeDataType> {

    // the handlers can only be created on the server
    private final EnumMap<EnumFacing, BeamNetHandler<NetType, ?>> handlers = new EnumMap<>(EnumFacing.class);
    private WeakReference<NetType> currentPipeNet = new WeakReference<>(null);
    private BeamNetHandler<NetType, ?> defaultHandler;

    private int ticksActive = 0;
    private int activeDuration = 0;
    private boolean disconnectScheduled;
    private boolean isActive = false;

    /** Creates the handler for a face, or for unsided queries when {@code facing} is null. */
    protected abstract BeamNetHandler<NetType, ?> createHandler(NetType net, @Nullable EnumFacing facing);

    /** The custom data discriminator that tells clients the pipe lit up or went dark. */
    protected abstract int getActiveDataCode();

    /** Whether the pipe, with the connections it has, may also connect on {@code side}: the shape of a line. */
    protected abstract boolean acceptsConnection(IPipeTile<?, ?> pipe, EnumFacing side);

    @Override
    public boolean supportsTicking() {
        return false;
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    private void initHandlers() {
        NetType net = getPipeNet();
        if (net == null) return;
        for (EnumFacing facing : EnumFacing.VALUES) {
            handlers.put(facing, createHandler(net, facing));
        }
        defaultHandler = createHandler(net, null);
    }

    /**
     * The server's handler for a face, or for unsided queries when {@code facing} is null; null while the pipe has
     * never been in a net.
     */
    @Nullable
    protected BeamNetHandler<NetType, ?> getNetHandler(@Nullable EnumFacing facing) {
        if (handlers.isEmpty()) {
            initHandlers();
        }

        checkNetwork();
        return handlers.getOrDefault(facing, defaultHandler);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            NetType current = getPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (BeamNetHandler<NetType, ?> handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    /** The net that holds this pipe; null on the client or outside a net. */
    @Nullable
    @SuppressWarnings("unchecked")
    public NetType getPipeNet() {
        if (world == null || world.isRemote) {
            return null;
        }
        NetType net = this.currentPipeNet.get();
        if (net != null && net.isValid() && net.containsNode(getPipePos())) {
            return net;
        }
        net = (NetType) getPipeBlock().getWorldPipeNet(getPipeWorld()).getNetFromPos(getPipePos());
        if (net != null) {
            this.currentPipeNet = new WeakReference<>(net);
        }
        return net;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void transferDataFrom(IPipeTile<PipeType, NodeDataType> tileEntity) {
        super.transferDataFrom(tileEntity);
        if (getPipeNet() == null) {
            return;
        }

        var pipe = (TileEntityBeamPipe<PipeType, NodeDataType, NetType>) tileEntity;
        if (!pipe.handlers.isEmpty() && pipe.defaultHandler != null) {
            // take handlers from old pipe
            handlers.clear();
            handlers.putAll(pipe.handlers);
            defaultHandler = pipe.defaultHandler;
            defaultHandler.updatePipe(this);
            for (BeamNetHandler<NetType, ?> handler : handlers.values()) {
                handler.updatePipe(this);
            }
            checkNetwork();
        } else {
            // create new handlers
            initHandlers();
        }
    }

    @Override
    public void setConnection(EnumFacing side, boolean connected, boolean fromNeighbor) {
        if (!getWorld().isRemote && connected && !fromNeighbor) {
            if (!acceptsConnection(this, side)) return;

            // check the same for the targeted pipe
            TileEntity tile = getWorld().getTileEntity(getPos().offset(side));
            if (tile instanceof IPipeTile<?, ?> pipeTile &&
                    pipeTile.getPipeType().getClass() == this.getPipeType().getClass() &&
                    !acceptsConnection(pipeTile, side.getOpposite())) {
                return;
            }
        }
        super.setConnection(side, connected, fromNeighbor);
    }

    public boolean isActive() {
        return this.isActive;
    }

    /**
     * @param active   if the pipe should become active
     * @param duration how long the pipe should be active for
     */
    public void setActive(boolean active, int duration) {
        if (this.isActive != active) {
            this.isActive = active;
            notifyBlockUpdate();
            markDirty();
            writeCustomData(getActiveDataCode(), buf -> buf.writeBoolean(this.isActive));
            if (active) scheduleDisconnect();
        }

        // each mark keeps a lit pipe lit for the full duration again; clients hear only of changes, and a chunk sent
        // to a client carries the lit state
        this.activeDuration = duration;
        if (duration > 0 && active) {
            this.ticksActive = 0;
        }
    }

    /** Schedule the task that turns the pipe off, unless one is already running. */
    private void scheduleDisconnect() {
        if (disconnectScheduled || getWorld() == null || getWorld().isRemote) return;
        disconnectScheduled = true;
        TaskScheduler.scheduleTask(getWorld(), this::queueDisconnect);
    }

    /** Counts a tick of the pipe's lit duration and turns it off when that ends; false ends the task. */
    public boolean queueDisconnect() {
        if (isInvalid()) {
            disconnectScheduled = false;
            return false;
        }
        if (++this.ticksActive % activeDuration == 0) {
            this.ticksActive = 0;
            disconnectScheduled = false;
            setActive(false, -1);
            return false;
        }
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // a pipe saved while active turns off after a while unless something feeds it again
        if (isActive && !getWorld().isRemote) {
            if (activeDuration <= 0) activeDuration = 100;
            scheduleDisconnect();
        }
    }

    @Override
    public void receiveCustomData(int discriminator, PacketBuffer buf) {
        super.receiveCustomData(discriminator, buf);
        if (discriminator == getActiveDataCode()) {
            this.isActive = buf.readBoolean();
            scheduleChunkForRenderUpdate();
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(this.isActive);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.isActive = buf.readBoolean();
        scheduleChunkForRenderUpdate();
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        compound.setBoolean("Active", isActive);
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Active", Constants.NBT.TAG_BYTE)) {
            isActive = compound.getBoolean("Active");
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        this.handlers.clear();
    }
}

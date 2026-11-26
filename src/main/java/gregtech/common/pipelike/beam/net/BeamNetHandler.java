package gregtech.common.pipelike.beam.net;

import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves one face of a beam pipe, or its unsided queries: it finds the endpoint at the other end of the line and
 * lights the line while requests reach that endpoint. The family's handler is the capability the pipe exposes, and
 * forwards each request to the endpoint's capability.
 */
public abstract class BeamNetHandler<NetType extends BeamPipeNet<?, RouteType>, RouteType extends BeamRoutePath<?>> {

    private NetType net;
    private TileEntityBeamPipe<?, ?, NetType> pipe;
    private final EnumFacing facing;

    protected BeamNetHandler(NetType net, @NotNull TileEntityBeamPipe<?, ?, NetType> pipe,
                             @Nullable EnumFacing facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
    }

    public void updateNetwork(NetType net) {
        this.net = net;
    }

    public void updatePipe(@NotNull TileEntityBeamPipe<?, ?, NetType> pipe) {
        this.pipe = pipe;
    }

    public NetType getNet() {
        return net;
    }

    /**
     * The route from this handler's face to the endpoint at the other end of the line; null when there is none, or
     * when the pipe is removed, unloaded, replaced, on a client or in no net.
     */
    @Nullable
    protected RouteType getRoute() {
        if (pipe == null || pipe.isInvalid() || !pipe.hasWorld() || pipe.getWorld().isRemote ||
                !pipe.getWorld().isBlockLoaded(pipe.getPipePos()) ||
                pipe.getWorld().getTileEntity(pipe.getPipePos()) != pipe) {
            return null;
        }

        this.net = pipe.getPipeNet();
        if (net == null || !net.isValid()) return null;
        return net.getNetData(pipe.getPipePos(), facing);
    }

    /** Lights the loaded pipes of the line, after a request through {@link #getRoute} reached the endpoint. */
    protected void setPipesActive() {
        // every request arrives here; walking the whole line once a second keeps it lit
        World world = pipe.getWorld();
        if (!net.shouldMarkActive(world.getTotalWorldTime())) return;
        for (BlockPos pos : net.getAllNodes().keySet()) {
            // the family's pipes, told apart by their pipe type as the pipe net framework does
            if (world.isBlockLoaded(pos) && world.getTileEntity(pos) instanceof TileEntityBeamPipe<?, ?, ?> line &&
                    line.getPipeTypeClass() == pipe.getPipeTypeClass()) {
                line.setActive(true, 100);
            }
        }
    }
}

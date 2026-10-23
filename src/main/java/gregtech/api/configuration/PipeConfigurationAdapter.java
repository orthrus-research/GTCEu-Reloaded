package gregtech.api.configuration;

import gregtech.api.cover.Cover;
import gregtech.api.pipenet.tile.IPipeTile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Plans local and reciprocal edges before using the normal setters without their recursive neighbor updates. */
public final class PipeConfigurationAdapter implements ConfigurationAdapter {

    private final IPipeTile<?, ?> pipe;
    private final String schema;

    public PipeConfigurationAdapter(IPipeTile<?, ?> pipe, String schema) {
        this.pipe = pipe;
        this.schema = schema;
    }

    @Override
    public String getSchema() {
        return schema;
    }

    @Override
    public NBTTagCompound capture() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Connections", pipe.getConnections());
        data.setInteger("Blocked", pipe.getBlockedConnections());
        return data;
    }

    @Override
    public void prepare(NBTTagCompound data, ConfigurationContext context, ConfigurationTransaction transaction) {
        ConfigurationData.keys(data, "Connections", "Blocked");
        int connections = ConfigurationData.integer(data, "Connections", 0, 63);
        int blocked = ConfigurationData.integer(data, "Blocked", 0, 63);
        ConfigurationData.require(pipe.canHaveBlockedFaces() && pipe.getPipeType() != null);
        Map<IPipeTile<?, ?>, MaskChange> planned = new LinkedHashMap<>();
        MaskChange local = new MaskChange(pipe);
        local.connections = connections;
        local.blocked = blocked;
        planned.put(pipe, local);
        BlockPos pos = pipe.getPipePos();
        for (EnumFacing side : EnumFacing.VALUES) {
            context.requireEditable(pos, side, transaction);
            if (bit(local.beforeConnections, side) == bit(connections, side)) continue;
            BlockPos neighborPos = pos.offset(side);
            // Check loading before any tile or neighbor-cache lookup; a paste never loads adjacent chunks.
            TileEntity neighbor = context.loadedTile(neighborPos);
            context.guardTile(neighborPos, neighbor, transaction);
            ConfigurationData.require(pipe.getNeighbor(side) == neighbor);
            transaction.guard(() -> pipe.getNeighbor(side) == neighbor);
            if (!(neighbor instanceof IPipeTile<?, ?>other)) continue;
            ConfigurationData.require(other != pipe && other.getPipeType() != null);
            ConfigurationAdapter otherAdapter = other.getConfigurationAdapter();
            ConfigurationData.require(otherAdapter != null && schema.equals(otherAdapter.getSchema()));
            context.requireEditable(neighborPos, side.getOpposite(), transaction);
            // Match setConnection: opening rejects unlike pipe-type enum families.
            ConfigurationData.require(!bit(connections, side) ||
                    other.getPipeType().getClass() == pipe.getPipeType().getClass());
            EnumFacing opposite = side.getOpposite();
            ConfigurationData.require(other.getNeighbor(opposite) == pipe);
            transaction.guard(() -> other.getNeighbor(opposite) == pipe);
            Cover cover = other.getCoverableImplementation().getCoverAtSide(opposite);
            transaction.guard(() -> other.getCoverableImplementation().getCoverAtSide(opposite) == cover);
            MaskChange reciprocal = planned.computeIfAbsent(other, MaskChange::new);
            boolean neighborOpen = bit(reciprocal.beforeConnections, opposite);
            boolean desired = bit(connections, side);
            // A covered neighbor keeps its open end when the local end is closed, just like the wrench.
            if (desired != neighborOpen && (!neighborOpen || cover == null)) {
                reciprocal.connections = withBit(reciprocal.connections, opposite, desired);
            }
        }
        for (MaskChange change : planned.values()) {
            IPipeTile<?, ?> target = change.pipe;
            Object pipeType = target.getPipeType();
            Object block = target.getPipeBlock();
            ConfigurationData.require(block != null);
            transaction.guard(() -> target.getPipeType() == pipeType && target.getPipeBlock() == block);
            context.guardTile(target.getPipePos(), (TileEntity) target, transaction);
            transaction.add(change);
        }
    }

    private static boolean bit(int mask, EnumFacing side) {
        return (mask & (1 << side.getIndex())) != 0;
    }

    private static int withBit(int mask, EnumFacing side, boolean value) {
        int bit = 1 << side.getIndex();
        return value ? mask | bit : mask & ~bit;
    }

    private static final class MaskChange implements ConfigurationChange {

        private final IPipeTile<?, ?> pipe;
        private final int beforeConnections;
        private final int beforeBlocked;
        private int connections;
        private int blocked;

        private MaskChange(IPipeTile<?, ?> pipe) {
            this.pipe = pipe;
            this.beforeConnections = this.connections = pipe.getConnections();
            this.beforeBlocked = this.blocked = pipe.getBlockedConnections();
        }

        @Override
        public boolean isCurrent() {
            return pipe.getConnections() == beforeConnections && pipe.getBlockedConnections() == beforeBlocked;
        }

        @Override
        public void apply() {
            setMasks(connections, blocked);
        }

        @Override
        public void rollback() {
            // Inverse normal notifications rebuild split/merged topology and invalidate routing caches.
            setMasks(beforeConnections, beforeBlocked);
        }

        private void setMasks(int connections, int blocked) {
            for (EnumFacing side : EnumFacing.VALUES) {
                if (pipe.isConnected(side) != bit(connections, side)) {
                    pipe.setConnection(side, bit(connections, side), true);
                }
                if (pipe.isFaceBlocked(side) != bit(blocked, side)) {
                    pipe.setFaceBlocked(side, bit(blocked, side));
                }
            }
            ConfigurationData.require(pipe.getConnections() == connections && pipe.getBlockedConnections() == blocked);
        }
    }
}

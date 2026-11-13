package gregtech.core.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkUtils {

    public static void writePacketBuffer(PacketBuffer writeTo, PacketBuffer writeFrom) {
        writeTo.writeVarInt(writeFrom.readableBytes());
        writeTo.writeBytes(writeFrom);
    }

    public static PacketBuffer readPacketBuffer(PacketBuffer buf) {
        // one copy, out of a slice of the incoming buffer
        return new PacketBuffer(Unpooled.copiedBuffer(buf.readSlice(buf.readVarInt())));
    }

    /** Squared distance within which vanilla accepts a player's block interaction. */
    private static final double MAX_REACH_SQ = 64.0;

    /**
     * Look up a tile entity a client packet names, without loading chunks or other dimensions.
     *
     * @return the tile entity at {@code pos} if it is in the player's own dimension and in a loaded chunk the
     *         player watches, otherwise {@code null}
     */
    public static @Nullable TileEntity getTileEntityWatchedBy(@NotNull EntityPlayerMP player, int dimension,
                                                              @NotNull BlockPos pos) {
        return isWatchedBy(player, dimension, pos) ? player.getServerWorld().getTileEntity(pos) : null;
    }

    /**
     * @return whether {@code pos} is in the player's own dimension and in a loaded chunk the player watches
     */
    public static boolean isWatchedBy(@NotNull EntityPlayerMP player, int dimension, @NotNull BlockPos pos) {
        if (player.dimension != dimension) return false;
        WorldServer world = player.getServerWorld();
        return world.isBlockLoaded(pos) &&
                world.getPlayerChunkMap().isPlayerWatchingChunk(player, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * @return as {@link #getTileEntityWatchedBy}, and only if the position is also within the player's reach
     */
    public static @Nullable TileEntity getTileEntityInReach(@NotNull EntityPlayerMP player, int dimension,
                                                            @NotNull BlockPos pos) {
        if (player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_REACH_SQ) return null;
        return getTileEntityWatchedBy(player, dimension, pos);
    }

    public static NetworkRegistry.TargetPoint blockPoint(World world, BlockPos blockPos) {
        return new NetworkRegistry.TargetPoint(world.provider.getDimension(), blockPos.getX() + 0.5,
                blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 128.0);
    }
}

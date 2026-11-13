package gregtech.api.items.toolitem;

import gregtech.api.util.TaskScheduler;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class TreeFellingListener {

    private final EntityPlayerMP player;
    private final ItemStack tool;
    private final Deque<BlockPos> orderedBlocks;
    private final World world;
    private final Block log;

    private TreeFellingListener(EntityPlayerMP player, ItemStack tool, Deque<BlockPos> orderedBlocks, World world,
                                Block log) {
        this.player = player;
        this.tool = tool;
        this.orderedBlocks = orderedBlocks;
        this.world = world;
        this.log = log;
    }

    public static void start(@NotNull IBlockState state, ItemStack tool, BlockPos start,
                             @NotNull EntityPlayerMP player) {
        World world = player.world;
        Block block = state.getBlock();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        Queue<BlockPos> checking = new ArrayDeque<>();
        Set<BlockPos> visited = new ObjectOpenHashSet<>();
        checking.add(start);

        while (!checking.isEmpty()) {
            BlockPos check = checking.remove();
            if (check != start) {
                visited.add(check);
            }
            for (int y = 0; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            mutablePos.setPos(check.getX() + x, check.getY() + y, check.getZ() + z);
                            if (!visited.contains(mutablePos)) {
                                // Check that the found block matches the original block state, which is wood.
                                if (block == world.getBlockState(mutablePos).getBlock()) {
                                    if (!checking.contains(mutablePos)) {
                                        BlockPos immutablePos = mutablePos.toImmutable();
                                        checking.add(immutablePos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!visited.isEmpty()) {
            Deque<BlockPos> orderedBlocks = visited.stream()
                    .sorted(Comparator.comparingInt(pos -> start.getY() - pos.getY()))
                    .collect(Collectors.toCollection(ArrayDeque::new));
            // the world's task list goes away with the world, unlike an event bus listener
            TaskScheduler.scheduleTask(world, new TreeFellingListener(player, tool, orderedBlocks, world, block)::tick);
        }
    }

    /**
     * Break the next queued log, once per world tick.
     *
     * @return whether there is more to fell
     */
    private boolean tick() {
        // stop when the player has left the tree's world, died or logged out
        if (orderedBlocks.isEmpty() || tool.isEmpty() || player.world != world || player.isDead ||
                player.hasDisconnected()) {
            return false;
        }
        // skip positions whose log is gone or was replaced since the tree was scanned
        while (!orderedBlocks.isEmpty()) {
            BlockPos pos = orderedBlocks.removeLast();
            if (world.getBlockState(pos).getBlock() == log) {
                ToolHelper.breakBlockRoutine(player, tool, pos);
                return !orderedBlocks.isEmpty();
            }
        }
        return false;
    }
}

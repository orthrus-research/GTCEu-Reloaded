package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetMutationSequenceTest {

    @Test
    void randomEditsMatchIndependentConnectivityAndChunkOracle() {
        for (long seed : new long[] { 1L, 39L, 317L, 20261026L }) {
            Random random = new Random(seed);
            PipeNetTestWorld world = new PipeNetTestWorld();
            for (int step = 0; step < 800; step++) {
                BlockPos pos = new BlockPos(random.nextInt(6) - 3, 64, random.nextInt(6) - 3);
                switch (random.nextInt(5)) {
                    case 0 -> world.addNode(pos, random.nextInt(3), random.nextInt(3), random.nextInt(64),
                            random.nextBoolean());
                    case 1 -> world.removeNode(pos);
                    case 2 -> world.updateMark(pos, random.nextInt(3));
                    case 3 -> world.updateBlockedConnections(pos, EnumFacing.VALUES[random.nextInt(6)],
                            random.nextBoolean());
                    case 4 -> {
                        PipeNetTestNetwork net = world.getNetFromPos(pos);
                        if (net != null) net.markNodeAsActive(pos, random.nextBoolean());
                    }
                }
                if (step % 47 == 0) {
                    world.readFromNBT(world.writeToNBT(new NBTTagCompound()));
                }
                assertGraph(world, "seed=" + seed + ", step=" + step);
            }
        }
    }

    private static void assertGraph(PipeNetTestWorld world, String context) {
        Map<BlockPos, Node<Integer>> allNodes = new HashMap<>();
        Map<ChunkPos, Integer> expectedChunkNetworks = new HashMap<>();
        for (PipeNetTestNetwork net : world.networks()) {
            assertTrue(net.isValid(), context);
            assertFalse(net.getAllNodes().isEmpty(), context);
            Set<ChunkPos> expectedChunks = new HashSet<>();
            net.getAllNodes().forEach((pos, node) -> {
                assertNull(allNodes.put(pos, node), context);
                assertSame(net, world.getNetFromPos(pos), context);
                expectedChunks.add(new ChunkPos(pos));
            });
            assertEquals(expectedChunks, net.getContainedChunks(), context);
            expectedChunks.forEach(chunk -> expectedChunkNetworks.merge(chunk, 1, Integer::sum));
        }
        assertEquals(expectedChunkNetworks.size(), world.indexedChunkCount(), context);
        expectedChunkNetworks.forEach((chunk, count) -> assertEquals(count.intValue(), world.networksInChunk(chunk),
                context));
        for (PipeNetTestNetwork net : world.networks()) {
            BlockPos origin = net.getAllNodes().keySet().iterator().next();
            Set<BlockPos> reached = reachable(origin, allNodes);
            assertEquals(reached, net.getAllNodes().keySet(), context);
        }
    }

    private static Set<BlockPos> reachable(BlockPos origin, Map<BlockPos, Node<Integer>> nodes) {
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        reached.add(origin);
        frontier.add(origin);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.removeFirst();
            Node<Integer> node = nodes.get(pos);
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos neighborPos = pos.offset(side);
                Node<Integer> neighbor = nodes.get(neighborPos);
                if (neighbor == null || node.isBlocked(side) || neighbor.isBlocked(side.getOpposite())) continue;
                if (node.mark != neighbor.mark && node.mark != 0 && neighbor.mark != 0) continue;
                if (reached.add(neighborPos)) frontier.addLast(neighborPos);
            }
        }
        return reached;
    }
}

package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetDenseLifecycleTest {

    @Test
    void denseNetworksKeepChunkOwnersThroughSplitMergeReloadAndRemoval() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        List<BlockPos> nodes = new ArrayList<>();
        BlockPos left = new BlockPos(-16, 64, -16);
        BlockPos right = new BlockPos(1, 64, -16);
        addCube(world, nodes, left);
        addCube(world, nodes, right);
        for (int x = -8; x <= 0; x++) add(world, nodes, new BlockPos(x, 64, -16));
        assertEquals(1, world.networkCount());
        assertIndex(world);

        BlockPos bridge = new BlockPos(-4, 64, -16);
        world.removeNode(bridge);
        assertEquals(2, world.networkCount());
        assertNotSame(world.getNetFromPos(left), world.getNetFromPos(right));
        assertEquals(2, world.networksInChunk(new ChunkPos(left)));
        assertIndex(world);

        world.readFromNBT(world.writeToNBT(new NBTTagCompound()));
        assertEquals(2, world.networkCount());
        assertIndex(world);
        world.addNode(bridge, 1, 1, 63, false);
        assertEquals(1, world.networkCount());
        assertSame(world.getNetFromPos(left), world.getNetFromPos(right));
        assertIndex(world);

        world.updateMark(bridge, 2);
        assertEquals(3, world.networkCount());
        assertIndex(world);
        world.updateMark(bridge, 0);
        assertEquals(1, world.networkCount());
        assertIndex(world);
        world.readFromNBT(world.writeToNBT(new NBTTagCompound()));
        assertIndex(world);

        Collections.shuffle(nodes, new Random(20261027L));
        for (int i = 0; i < nodes.size(); i++) {
            world.removeNode(nodes.get(i));
            if (i % 31 == 0) assertIndex(world);
        }
        assertEquals(0, world.networkCount());
        assertEquals(0, world.indexedChunkCount());
        assertTrue(world.writeToNBT(new NBTTagCompound()).getTagList("PipeNets", 10).isEmpty());
    }

    private static void addCube(PipeNetTestWorld world, List<BlockPos> nodes, BlockPos origin) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 8; z++) add(world, nodes, origin.add(x, y, z));
            }
        }
    }

    private static void add(PipeNetTestWorld world, List<BlockPos> nodes, BlockPos pos) {
        nodes.add(pos);
        world.addNode(pos, 1, 1, 63, false);
    }

    private static void assertIndex(PipeNetTestWorld world) {
        Map<ChunkPos, Integer> owners = new HashMap<>();
        for (PipeNetTestNetwork net : world.networks()) {
            Set<ChunkPos> expected = new HashSet<>();
            for (BlockPos pos : net.getAllNodes().keySet()) {
                assertSame(net, world.getNetFromPos(pos));
                expected.add(new ChunkPos(pos));
            }
            assertEquals(expected, net.getContainedChunks());
            expected.forEach(chunk -> owners.merge(chunk, 1, Integer::sum));
        }
        assertEquals(owners.size(), world.indexedChunkCount());
        owners.forEach((chunk, count) -> assertEquals(count.intValue(), world.networksInChunk(chunk)));
    }
}

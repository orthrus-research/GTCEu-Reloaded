package gregtech.api.pipenet;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetCorrectnessTest {

    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final int ALL_FACES = 63;

    @Test
    void closingAlreadyIncompatibleLoopEdgeKeepsRequestedFaceClosed() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, ALL_FACES, false);
        world.addNode(A.south(), 1, 0, ALL_FACES, false);
        world.addNode(A.south().east(), 1, 0, ALL_FACES, false);
        world.addNode(A.east(), 1, 0, ALL_FACES & ~bit(EnumFacing.WEST), false);
        assertSame(world.getNetFromPos(A), world.getNetFromPos(A.east()));

        world.updateBlockedConnections(A, EnumFacing.EAST, true);

        assertTrue(world.getNetFromPos(A).getNodeAt(A).isBlocked(EnumFacing.EAST));
        assertSame(world.getNetFromPos(A), world.getNetFromPos(A.east()));
    }

    @Test
    void endpointOnlyEditDirtiesSavedGraphAndChangesStampWithoutSleeping() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, ALL_FACES, false);
        PipeNetTestNetwork net = world.getNetFromPos(A);
        long before = net.getLastUpdate();
        world.setDirty(false);

        world.updateBlockedConnections(A, EnumFacing.UP, true);

        assertTrue(world.isDirty());
        assertTrue(net.getNodeAt(A).isBlocked(EnumFacing.UP));
        assertTrue(net.getLastUpdate() > before);
    }

    @Test
    void identicalDuplicateInsertionIsIdempotent() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, 0, false);
        PipeNetTestNetwork original = world.getNetFromPos(A);
        world.setDirty(false);

        world.addNode(A, 1, 0, 0, false);

        assertSame(original, world.getNetFromPos(A));
        assertEquals(1, world.networkCount());
        assertEquals(1, world.networksInChunk(new ChunkPos(A)));
        assertFalse(world.isDirty());
    }

    @Test
    void changedDuplicateReplacesOneOwnerAndKeepsChunkAccountingExact() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, ALL_FACES, false);
        world.addNode(A.east(), 1, 0, ALL_FACES, false);

        world.addNode(A.east(), 2, 0, ALL_FACES, true);

        assertEquals(1, world.networkCount());
        PipeNetTestNetwork net = world.getNetFromPos(A);
        assertEquals(2, net.getAllNodes().size());
        assertEquals(Integer.valueOf(2), net.getNodeAt(A.east()).data);
        assertTrue(net.getNodeAt(A.east()).isActive);
        world.removeNode(A);
        world.removeNode(A.east());
        assertTrue(net.getContainedChunks().isEmpty());
        assertEquals(0, world.networkCount());
        assertEquals(0, world.indexedChunkCount());
    }

    @Test
    void lowLevelReplacementAndAbsentRemovalDoNotCorruptChunkCounts() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, 0, false);
        PipeNetTestNetwork net = world.getNetFromPos(A);

        net.addSilently(A, new Node<>(2, 0, 0, false));
        net.removeSilently(A.east());
        assertEquals(1, net.getAllNodes().size());
        assertEquals(1, net.getContainedChunks().size());
        net.removeSilently(A);
        assertTrue(net.getContainedChunks().isEmpty());
        assertEquals(0, world.indexedChunkCount());
    }

    @Test
    void mutableCoordinateInputsDoNotMoveStoredKeys() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(A);
        world.addNode(mutable, 1, 0, 0, false);

        mutable.setPos(1024, -5000, -1024);

        assertNotNull(world.getNetFromPos(A));
        assertNull(world.getNetFromPos(mutable));
    }

    @Test
    void denseNegativeChunkKeepsLiveViewsAndOwnershipUntilItsLastNodeIsRemoved() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        BlockPos origin = new BlockPos(-32, -5000, -32);
        world.addNode(origin, 1, 0, 0, false);
        PipeNetTestNetwork net = world.getNetFromPos(origin);
        Set<ChunkPos> chunks = net.getContainedChunks();
        Map<BlockPos, Node<Integer>> nodes = net.getAllNodes();
        for (int i = 1; i < 256; i++) {
            net.addSilently(origin.add(i & 15, 0, i >> 4), new Node<>(1, 0, 0, false));
        }
        net.addSilently(origin, new Node<>(2, 0, 0, false));

        assertEquals(256, nodes.size());
        assertEquals(1, chunks.size());
        assertEquals(1, world.networksInChunk(new ChunkPos(origin)));
        assertThrows(UnsupportedOperationException.class, chunks::clear);
        assertThrows(UnsupportedOperationException.class, nodes::clear);
        for (int i = 1; i < 256; i++) net.removeSilently(origin.add(i & 15, 0, i >> 4));

        assertEquals(1, nodes.size());
        assertEquals(1, chunks.size());
        assertSame(net, world.getNetFromPos(origin));
        net.removeSilently(origin);
        assertTrue(nodes.isEmpty());
        assertTrue(chunks.isEmpty());
        assertEquals(0, world.indexedChunkCount());
    }

    @Test
    void liveNodeLookupRetainsDistinctPositionsOutsidePackedHeightRange() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        BlockPos lower = new BlockPos(-17, -5000, -17);
        BlockPos upper = lower.up(4096);
        assertEquals(lower.toLong(), upper.toLong());
        world.addNode(lower, 1, 0, 0, false);
        PipeNetTestNetwork net = world.getNetFromPos(lower);
        net.addSilently(upper, new Node<>(2, 0, 0, false));

        assertEquals(Integer.valueOf(1), net.getNodeAt(lower).data);
        assertEquals(Integer.valueOf(2), net.getNodeAt(upper).data);
        net.removeSilently(lower);
        assertNull(net.getNodeAt(lower));
        assertSame(net, world.getNetFromPos(upper));
        net.removeSilently(upper);
        assertEquals(0, world.indexedChunkCount());
    }

    @Test
    void broadcastReachesOtherPaintedComponentsWithoutRecursing() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 10, ALL_FACES, false);
        world.addNode(A.east(), 1, 20, ALL_FACES, false);
        PipeNetTestNetwork first = world.getNetFromPos(A);
        PipeNetTestNetwork second = world.getNetFromPos(A.east());
        assertNotSame(first, second);
        first.requestNestedInvalidation = true;
        int firstBefore = first.routeInvalidations;
        int secondBefore = second.routeInvalidations;

        world.invalidateRoutes();

        assertEquals(firstBefore + 1, first.routeInvalidations);
        assertEquals(secondBefore + 1, second.routeInvalidations);
    }

    @Test
    void explicitFaceInvalidationReachesItsChangedNetAndCrossingComponentsOnce() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 10, ALL_FACES, false);
        world.addNode(A.east(), 1, 20, ALL_FACES, false);
        PipeNetTestNetwork first = world.getNetFromPos(A);
        PipeNetTestNetwork second = world.getNetFromPos(A.east());
        first.requestNestedInvalidation = true;
        int firstBefore = first.routeInvalidations;
        int secondBefore = second.routeInvalidations;

        world.invalidateRoutesAt(A);

        assertEquals(firstBefore + 1, first.routeInvalidations);
        assertEquals(secondBefore + 1, second.routeInvalidations);
        world.invalidateRoutesAt(A.up());
        assertEquals(firstBefore + 1, first.routeInvalidations);
    }

    @Test
    void bridgeDisconnectAndReconnectKeepBothSidesDiscoverable() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, ALL_FACES, false);
        world.addNode(A.east(), 1, 0, ALL_FACES, false);

        world.updateBlockedConnections(A, EnumFacing.EAST, true);
        assertNotSame(world.getNetFromPos(A), world.getNetFromPos(A.east()));
        world.updateBlockedConnections(A, EnumFacing.EAST, false);

        assertSame(world.getNetFromPos(A), world.getNetFromPos(A.east()));
        assertEquals(1, world.networkCount());
    }

    @Test
    void invalidRuntimeMaskIsRejectedBeforeExistingNodeChanges() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, 0, false);
        PipeNetTestNetwork original = world.getNetFromPos(A);

        assertThrows(IllegalArgumentException.class, () -> world.addNode(A, 2, 0, 64, false));

        assertSame(original, world.getNetFromPos(A));
        assertEquals(Integer.valueOf(1), original.getNodeAt(A).data);
    }

    private static int bit(EnumFacing face) {
        return 1 << face.getIndex();
    }
}

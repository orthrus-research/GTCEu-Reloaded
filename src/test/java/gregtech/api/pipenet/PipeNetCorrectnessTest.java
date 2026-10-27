package gregtech.api.pipenet;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import org.junit.jupiter.api.Test;

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

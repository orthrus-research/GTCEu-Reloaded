package gregtech.api.pipenet.tickable;

import gregtech.Bootstrap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class TickableWorldPipeNetTest {

    private static final BlockPos A = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void worldEventRunsOnceAtEndPhaseAndIgnoresDuplicateRegistration() throws ReflectiveOperationException {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        TickablePipeFixtureNetwork net = graph.getNetFromPos(A);
        Function<World, TickableWorldPipeNet<?, ?>> accessor = ignored -> graph;
        TickableWorldPipeNetEventHandler.registerTickablePipeNet(accessor);
        TickableWorldPipeNetEventHandler.registerTickablePipeNet(accessor);
        try {
            TickableWorldPipeNetEventHandler.onWorldTick(
                    new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, world));
            assertEquals(0, net.updates);

            TickableWorldPipeNetEventHandler.onWorldTick(
                    new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

            assertEquals(1, net.updates);
        } finally {
            Field accessors = TickableWorldPipeNetEventHandler.class.getDeclaredField("pipeNetAccessors");
            accessors.setAccessible(true);
            ((List<?>) accessors.get(null)).remove(accessor);
        }
    }

    @Test
    void duplicateChunkLoadsNeedOnlyOneUnloadToStopTicking() {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        TickablePipeFixtureNetwork net = graph.getNetFromPos(A);
        Chunk chunk = new Chunk(world, 0, 0);
        graph.onChunkLoaded(chunk);
        graph.onChunkLoaded(chunk);

        world.loaded.clear();
        graph.onChunkUnloaded(chunk);
        graph.update();

        assertEquals(0, net.updates);
    }

    @Test
    void chunkReloadBeforeNextTickCancelsPendingRemoval() {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        TickablePipeFixtureNetwork net = graph.getNetFromPos(A);
        Chunk chunk = new Chunk(world, 0, 0);
        world.loaded.clear();
        graph.onChunkUnloaded(chunk);

        world.loaded.add(new ChunkPos(A));
        graph.onChunkLoaded(chunk);
        graph.update();

        assertEquals(1, net.updates);
    }

    @Test
    void removedNetworkDoesNotReceiveOneFinalTick() {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        TickablePipeFixtureNetwork original = graph.getNetFromPos(A);

        graph.removeNode(A);
        graph.update();

        assertFalse(original.isValid());
        assertEquals(0, original.updates);
    }

    @Test
    void repeatNbtReadTicksOnlyTheRestoredNetwork() {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        TickablePipeFixtureNetwork original = graph.getNetFromPos(A);
        NBTTagCompound saved = graph.writeToNBT(new NBTTagCompound());

        graph.readFromNBT(saved);
        TickablePipeFixtureNetwork restored = graph.getNetFromPos(A);
        graph.update();

        assertFalse(original.isValid());
        assertEquals(0, original.updates);
        assertEquals(1, restored.updates);
    }

    @Test
    void worldRebindingRebuildsLoadedMembershipInsteadOfKeepingOldTickers() {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        TickablePipeFixtureNetwork net = graph.getNetFromPos(A);

        graph.attach(new TickablePipeFixtureWorld());
        graph.update();

        assertEquals(0, net.updates);
    }

    @Test
    void callbackRemovalDuringTickSkipsRemovedPeerWithoutConcurrentModification() {
        TickablePipeFixtureWorld world = loadedWorld();
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(A, 1, 0, 0, false);
        graph.addNode(A.east(), 1, 0, 0, false);
        TickablePipeFixtureNetwork first = graph.getNetFromPos(A);
        TickablePipeFixtureNetwork second = graph.getNetFromPos(A.east());
        first.action = () -> graph.removeNode(A.east());
        second.action = () -> graph.removeNode(A);

        assertDoesNotThrow(graph::update);

        assertEquals(1, first.updates + second.updates);
    }

    @Test
    void updateRateAndDetachedWorldHaveExplicitBehavior() {
        TickablePipeFixtureGraph detached = new TickablePipeFixtureGraph();
        assertDoesNotThrow(detached::update);
        TickablePipeFixtureGraph graph = graph(loadedWorld());
        graph.rate = 0;

        assertThrows(IllegalStateException.class, graph::update);
    }

    @Test
    void equalDistinctNetworksBothTickAndRemovingOnePreservesItsPeer() {
        BlockPos secondPos = A.east(32);
        TickablePipeFixtureWorld world = loadedWorld();
        world.loaded.add(new ChunkPos(secondPos));
        TickablePipeFixtureGraph graph = graph(world);
        graph.valueEqualNetworks = true;
        graph.addNode(A, 1, 0, 0, false);
        graph.addNode(secondPos, 1, 0, 0, false);
        TickablePipeFixtureNetwork first = graph.getNetFromPos(A);
        TickablePipeFixtureNetwork second = graph.getNetFromPos(secondPos);
        assertNotSame(first, second);
        assertEquals(first, second, "The extension deliberately defines value equality");

        graph.update();

        assertEquals(1, first.updates);
        assertEquals(1, second.updates);
        graph.removeNode(secondPos);
        graph.update();
        assertSame(first, graph.getNetFromPos(A));
        assertTrue(first.isValid());
        assertFalse(second.isValid());
        assertEquals(2, first.updates);
        assertEquals(1, second.updates);
    }

    @Test
    void equalDistinctNetworksUnloadAndReloadWithoutChangingTheirPeersTickingState() {
        BlockPos secondPos = A.east(32);
        ChunkPos secondChunk = new ChunkPos(secondPos);
        TickablePipeFixtureWorld world = loadedWorld();
        world.loaded.add(secondChunk);
        TickablePipeFixtureGraph graph = graph(world);
        graph.valueEqualNetworks = true;
        graph.addNode(A, 1, 0, 0, false);
        graph.addNode(secondPos, 1, 0, 0, false);
        TickablePipeFixtureNetwork first = graph.getNetFromPos(A);
        TickablePipeFixtureNetwork second = graph.getNetFromPos(secondPos);
        graph.update();

        world.loaded.remove(secondChunk);
        graph.onChunkUnloaded(new Chunk(world, secondChunk.x, secondChunk.z));
        graph.update();

        assertEquals(2, first.updates);
        assertEquals(1, second.updates);
        world.loaded.add(secondChunk);
        graph.onChunkLoaded(new Chunk(world, secondChunk.x, secondChunk.z));
        graph.update();
        assertEquals(3, first.updates);
        assertEquals(2, second.updates);
    }

    @Test
    void equalDistinctNetworksRestoreSeparateTickersOnNbtReload() {
        BlockPos secondPos = A.east(32);
        TickablePipeFixtureWorld world = loadedWorld();
        world.loaded.add(new ChunkPos(secondPos));
        TickablePipeFixtureGraph graph = graph(world);
        graph.valueEqualNetworks = true;
        graph.addNode(A, 1, 0, 0, false);
        graph.addNode(secondPos, 1, 0, 0, false);
        TickablePipeFixtureNetwork oldFirst = graph.getNetFromPos(A);
        TickablePipeFixtureNetwork oldSecond = graph.getNetFromPos(secondPos);
        NBTTagCompound saved = graph.writeToNBT(new NBTTagCompound());

        graph.readFromNBT(saved);
        TickablePipeFixtureNetwork first = graph.getNetFromPos(A);
        TickablePipeFixtureNetwork second = graph.getNetFromPos(secondPos);
        graph.update();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertFalse(oldFirst.isValid());
        assertFalse(oldSecond.isValid());
        assertEquals(0, oldFirst.updates);
        assertEquals(0, oldSecond.updates);
        assertEquals(1, first.updates);
        assertEquals(1, second.updates);
    }

    private static TickablePipeFixtureGraph graph(TickablePipeFixtureWorld world) {
        TickablePipeFixtureGraph graph = new TickablePipeFixtureGraph();
        graph.attach(world);
        return graph;
    }

    @Test
    void removingTheLastNodeInTheOnlyLoadedChunkStopsTheRemainingNetwork() {
        BlockPos left = new BlockPos(-1, 64, -1);
        BlockPos right = left.east();
        TickablePipeFixtureWorld world = new TickablePipeFixtureWorld();
        ChunkPos leftChunk = new ChunkPos(left);
        ChunkPos rightChunk = new ChunkPos(right);
        world.loaded.add(leftChunk);
        world.loaded.add(rightChunk);
        TickablePipeFixtureGraph graph = graph(world);
        graph.addNode(left, 1, 0, 63, false);
        graph.addNode(right, 1, 0, 63, false);
        TickablePipeFixtureNetwork net = graph.getNetFromPos(left);
        assertSame(net, graph.getNetFromPos(right));
        graph.update();
        assertEquals(1, net.updates);

        world.loaded.remove(leftChunk);
        graph.onChunkUnloaded(new Chunk(world, leftChunk.x, leftChunk.z));
        graph.update();
        assertEquals(2, net.updates);
        world.loaded.remove(rightChunk);
        graph.onChunkUnloaded(new Chunk(world, rightChunk.x, rightChunk.z));
        graph.update();
        assertEquals(2, net.updates);

        world.loaded.add(leftChunk);
        graph.onChunkLoaded(new Chunk(world, leftChunk.x, leftChunk.z));
        graph.update();
        assertEquals(3, net.updates);
        graph.removeNode(left);
        graph.update();
        assertEquals(3, net.updates);
        assertSame(net, graph.getNetFromPos(right));
        assertFalse(net.getContainedChunks().contains(leftChunk));
    }

    private static TickablePipeFixtureWorld loadedWorld() {
        TickablePipeFixtureWorld world = new TickablePipeFixtureWorld();
        world.loaded.add(new ChunkPos(A));
        return world;
    }
}

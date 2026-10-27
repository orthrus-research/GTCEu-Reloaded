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

    private static TickablePipeFixtureGraph graph(TickablePipeFixtureWorld world) {
        TickablePipeFixtureGraph graph = new TickablePipeFixtureGraph();
        graph.attach(world);
        return graph;
    }

    private static TickablePipeFixtureWorld loadedWorld() {
        TickablePipeFixtureWorld world = new TickablePipeFixtureWorld();
        world.loaded.add(new ChunkPos(A));
        return world;
    }
}

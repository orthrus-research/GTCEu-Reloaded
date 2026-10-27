package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetPersistenceTest {

    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos DISTANT = new BlockPos(32, -5000, -32);

    @Test
    void rereadingEmptyWorldInvalidatesOldNetsAndRemovesGhostIndexes() {
        PipeNetTestWorld world = worldWithOneNode();
        PipeNetTestNetwork original = world.getNetFromPos(A);

        world.readFromNBT(new PipeNetTestWorld().writeToNBT(new NBTTagCompound()));

        assertFalse(original.isValid());
        assertNull(world.getNetFromPos(A));
        assertEquals(0, world.networkCount());
        assertEquals(0, world.indexedChunkCount());
    }

    @Test
    void rereadingSameWorldReplacesRatherThanDuplicatesChunkEntries() {
        PipeNetTestWorld world = worldWithOneNode();
        PipeNetTestNetwork original = world.getNetFromPos(A);
        NBTTagCompound saved = world.writeToNBT(new NBTTagCompound());

        world.readFromNBT(saved);

        assertFalse(original.isValid());
        assertNotSame(original, world.getNetFromPos(A));
        assertEquals(1, world.networkCount());
        assertEquals(1, world.indexedChunkCount());
    }

    @Test
    void registeredNetReloadReindexesItsChunksAndNotifiesRoutes() {
        PipeNetTestWorld world = worldWithOneNode();
        PipeNetTestNetwork original = world.getNetFromPos(A);
        PipeNetTestWorld donor = new PipeNetTestWorld();
        donor.addNode(DISTANT, 7, 0x123456, 5, true);
        world.setDirty(false);
        int invalidations = original.routeInvalidations;

        original.deserializeNBT(donor.getNetFromPos(DISTANT).serializeNBT());

        assertNull(world.getNetFromPos(A));
        assertSame(original, world.getNetFromPos(DISTANT));
        assertEquals(1, world.indexedChunkCount());
        assertTrue(world.isDirty());
        assertTrue(original.routeInvalidations > invalidations);
        assertEquals(Integer.valueOf(7), original.getNodeAt(DISTANT).data);
    }

    @Test
    void integerCoordinateDomainAndOptionalDefaultsRoundTrip() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        BlockPos extreme = new BlockPos(Integer.MAX_VALUE, Integer.MIN_VALUE, -90000000);
        world.addNode(extreme, 19, 0, 0, false);
        PipeNetTestWorld restored = new PipeNetTestWorld();

        restored.readFromNBT(world.writeToNBT(new NBTTagCompound()));

        Node<Integer> node = restored.getNetFromPos(extreme).getNodeAt(extreme);
        assertEquals(Integer.valueOf(19), node.data);
        assertEquals(0, node.openConnections);
        assertEquals(0, node.mark);
        assertFalse(node.isActive);
    }

    @Test
    void missingAndWrongPropertyReferencesAreRejectedWithoutReplacingWorld() {
        assertWorldReadRejected(tag -> node(tag).setInteger("index", 91));
        assertWorldReadRejected(tag -> property(tag).removeTag("index"));
        assertWorldReadRejected(tag -> property(tag).setString("index", "0"));
        assertWorldReadRejected(tag -> property(tag).removeTag("value"));
    }

    @Test
    void duplicatePropertyIndexesAndPositionsAreRejectedExplicitly() {
        assertWorldReadRejected(tag -> properties(tag).appendTag(property(tag).copy()));
        assertWorldReadRejected(tag -> nodes(tag).appendTag(node(tag).copy()));
        assertWorldReadRejected(tag -> tag.getTagList("PipeNets", 10)
                .appendTag(tag.getTagList("PipeNets", 10).getCompoundTagAt(0).copy()));
    }

    @Test
    void wrongListTypesAndCoordinateTypesAreRejectedExplicitly() {
        assertWorldReadRejected(tag -> tag.setString("PipeNets", "invalid"));
        assertWorldReadRejected(tag -> {
            NBTTagList list = new NBTTagList();
            list.appendTag(new NBTTagString("invalid"));
            tag.setTag("PipeNets", list);
        });
        assertWorldReadRejected(tag -> component(tag).setString("Nodes", "invalid"));
        assertWorldReadRejected(tag -> compound(tag).setString("NodeIndexes", "invalid"));
        assertWorldReadRejected(tag -> {
            NBTTagList list = new NBTTagList();
            list.appendTag(new NBTTagString("invalid"));
            compound(tag).setTag("WireProperties", list);
        });
        assertWorldReadRejected(tag -> node(tag).setString("y", "64"));
    }

    @Test
    void invalidMasksAndOptionalFieldTypesAreRejectedExplicitly() {
        assertWorldReadRejected(tag -> node(tag).setInteger("open", 64));
        assertWorldReadRejected(tag -> node(tag).setInteger("open", -1));
        assertWorldReadRejected(tag -> node(tag).setString("mark", "0"));
        assertWorldReadRejected(tag -> node(tag).setInteger("active", 1));
    }

    @Test
    void registeredNetMalformedReloadRestoresMembershipAndDirtyState() {
        PipeNetTestWorld world = worldWithOneNode();
        PipeNetTestNetwork original = world.getNetFromPos(A);
        NBTTagCompound invalid = original.serializeNBT();
        invalid.getCompoundTag("Nodes").getTagList("NodeIndexes", 10).getCompoundTagAt(0)
                .setInteger("index", 8);
        world.setDirty(false);
        long before = original.getLastUpdate();

        assertThrows(IllegalArgumentException.class, () -> original.deserializeNBT(invalid));

        assertSame(original, world.getNetFromPos(A));
        assertEquals(1, world.indexedChunkCount());
        assertEquals(before, original.getLastUpdate());
        assertFalse(world.isDirty());
    }

    private static void assertWorldReadRejected(Consumer<NBTTagCompound> corrupt) {
        PipeNetTestWorld world = worldWithOneNode();
        PipeNetTestNetwork original = world.getNetFromPos(A);
        NBTTagCompound saved = world.writeToNBT(new NBTTagCompound());
        corrupt.accept(saved);
        world.setDirty(false);

        assertThrows(IllegalArgumentException.class, () -> world.readFromNBT(saved));

        assertSame(original, world.getNetFromPos(A));
        assertTrue(original.isValid());
        assertEquals(1, world.networkCount());
        assertEquals(1, world.indexedChunkCount());
        assertFalse(world.isDirty());
    }

    private static PipeNetTestWorld worldWithOneNode() {
        PipeNetTestWorld world = new PipeNetTestWorld();
        world.addNode(A, 1, 0, 63, true);
        return world;
    }

    private static NBTTagCompound component(NBTTagCompound world) {
        return world.getTagList("PipeNets", 10).getCompoundTagAt(0);
    }

    private static NBTTagCompound compound(NBTTagCompound world) {
        return component(world).getCompoundTag("Nodes");
    }

    private static NBTTagList nodes(NBTTagCompound world) {
        return compound(world).getTagList("NodeIndexes", 10);
    }

    private static NBTTagCompound node(NBTTagCompound world) {
        return nodes(world).getCompoundTagAt(0);
    }

    private static NBTTagList properties(NBTTagCompound world) {
        return compound(world).getTagList("WireProperties", 10);
    }

    private static NBTTagCompound property(NBTTagCompound world) {
        return properties(world).getCompoundTagAt(0);
    }
}

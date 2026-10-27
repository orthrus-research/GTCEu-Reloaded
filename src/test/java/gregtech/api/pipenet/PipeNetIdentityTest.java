package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetIdentityTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @Test
    void distinctEqualComponentsPublishAndRemainDiscoverableInTheSameChunk() {
        EqualDistinctWorld graph = pair();
        EqualDistinctNetwork first = graph.getNetFromPos(POS);
        EqualDistinctNetwork second = graph.getNetFromPos(POS.east());

        assertNotNull(second);
        assertNotSame(first, second);
        assertEquals(first, second, "The fixture deliberately overrides logical equality");
        assertTrue(first.isValid() && second.isValid());
        assertEquals(2, graph.networkCount());
        assertEquals(2, graph.chunkOwners(POS));
    }

    @Test
    void removingTheSecondEqualComponentPreservesTheFirstOwnerAndSavedNodes() {
        EqualDistinctWorld graph = pair();
        EqualDistinctNetwork first = graph.getNetFromPos(POS);
        EqualDistinctNetwork second = graph.getNetFromPos(POS.east());

        graph.removeNode(POS.east());

        assertSame(first, graph.getNetFromPos(POS));
        assertTrue(first.isValid());
        assertFalse(second.isValid());
        assertNull(graph.getNetFromPos(POS.east()));
        assertEquals(1, graph.networkCount());
        assertEquals(1, graph.chunkOwners(POS));
        assertEquals(1, graph.writeToNBT(new NBTTagCompound()).getTagList("PipeNets", 10).tagCount());
    }

    @Test
    void splittingAndRemovingTheSecondEqualComponentPreservesUnrelatedOwners() {
        EqualDistinctWorld graph = new EqualDistinctWorld();
        graph.addNode(POS, 1, 10, 63, false);
        EqualDistinctNetwork first = graph.getNetFromPos(POS);
        for (int offset = 4; offset <= 6; offset++) graph.addNode(POS.east(offset), 1, 20, 63, false);

        graph.removeNode(POS.east(5));

        assertSame(first, graph.getNetFromPos(POS));
        assertNotNull(graph.getNetFromPos(POS.east(4)));
        assertNotNull(graph.getNetFromPos(POS.east(6)));
        assertNotSame(graph.getNetFromPos(POS.east(4)), graph.getNetFromPos(POS.east(6)));
        assertEquals(3, graph.networkCount());
        assertEquals(3, graph.chunkOwners(POS));
        graph.removeNode(POS.east(4));
        assertSame(first, graph.getNetFromPos(POS));
        assertNotNull(graph.getNetFromPos(POS.east(6)));
        assertEquals(2, graph.networkCount());
        assertEquals(2, graph.chunkOwners(POS));
    }

    @Test
    void republishingTheSameIdentityIsStillIdempotent() {
        EqualDistinctWorld graph = pair();
        graph.republish(graph.getNetFromPos(POS.east()));
        assertEquals(2, graph.networkCount());
        assertEquals(2, graph.chunkOwners(POS));
    }

    private static EqualDistinctWorld pair() {
        EqualDistinctWorld graph = new EqualDistinctWorld();
        graph.addNode(POS, 1, 10, 63, false);
        graph.addNode(POS.east(), 1, 20, 63, false);
        return graph;
    }
}

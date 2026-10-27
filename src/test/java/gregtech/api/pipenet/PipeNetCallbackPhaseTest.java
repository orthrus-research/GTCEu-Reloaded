package gregtech.api.pipenet;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetCallbackPhaseTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @Test
    void closingAnEdgeNotifiesTheOriginalParentAfterSplitPublication() {
        PipeNetPhaseWorld graph = line();
        PipeNetPhaseNetwork original = graph.getNetFromPos(POS);
        original.observe(POS.east(), POS, false);

        graph.updateBlockedConnections(POS, EnumFacing.EAST, true);

        assertTrue(original.observedCallbacks > 0);
        assertTrue(original.callbacksAfterPublication, "The original topology hook follows split publication");
        assertNotSame(original, graph.getNetFromPos(POS));
        assertSame(original, graph.getNetFromPos(POS.east()));
    }

    @Test
    void reopeningAnEdgeNotifiesItsReceiverAfterMergeCompletion() {
        PipeNetPhaseWorld graph = line();
        graph.updateBlockedConnections(POS, EnumFacing.EAST, true);
        PipeNetPhaseNetwork receiver = graph.getNetFromPos(POS);
        receiver.observe(POS, POS.east(), true);

        graph.updateBlockedConnections(POS, EnumFacing.EAST, false);

        assertTrue(receiver.observedCallbacks > 0);
        assertTrue(receiver.callbacksAfterPublication, "The original topology hook follows merge completion");
        assertSame(receiver, graph.getNetFromPos(POS.east()));
    }

    private static PipeNetPhaseWorld line() {
        PipeNetPhaseWorld graph = new PipeNetPhaseWorld();
        graph.addNode(POS, 1, 0, 63, false);
        graph.addNode(POS.east(), 1, 0, 63, false);
        return graph;
    }
}

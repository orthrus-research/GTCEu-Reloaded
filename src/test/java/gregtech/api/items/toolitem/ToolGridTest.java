package gregtech.api.items.toolitem;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolGridTest {

    @Test
    void everyViewedFaceMapsCenterEdgesAndCornersToTheClickTargets() {
        // Expected left, top, right and bottom targets in the legacy renderer's face orientation.
        EnumFacing[][] edges = {
                { EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.NORTH }, // DOWN
                { EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH }, // UP
                { EnumFacing.EAST, EnumFacing.UP, EnumFacing.WEST, EnumFacing.DOWN }, // NORTH
                { EnumFacing.WEST, EnumFacing.UP, EnumFacing.EAST, EnumFacing.DOWN }, // SOUTH
                { EnumFacing.NORTH, EnumFacing.UP, EnumFacing.SOUTH, EnumFacing.DOWN }, // WEST
                { EnumFacing.SOUTH, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.DOWN } // EAST
        };
        for (EnumFacing face : EnumFacing.VALUES) {
            assertEquals(face, ToolGrid.sideAtCell(face, 0, 0));
            assertEquals(edges[face.getIndex()][0], ToolGrid.sideAtCell(face, -1, 0));
            assertEquals(edges[face.getIndex()][1], ToolGrid.sideAtCell(face, 0, 1));
            assertEquals(edges[face.getIndex()][2], ToolGrid.sideAtCell(face, 1, 0));
            assertEquals(edges[face.getIndex()][3], ToolGrid.sideAtCell(face, 0, -1));
            for (int column : new int[] { -1, 1 }) {
                for (int row : new int[] { -1, 1 }) {
                    assertEquals(face.getOpposite(), ToolGrid.sideAtCell(face, column, row));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ToolGrid.sideAtCell(EnumFacing.UP, 2, 0));
    }
}

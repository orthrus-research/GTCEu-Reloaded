package gregtech.api.util;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumByOrdinalTest {

    @Test
    public void inRangeOrdinalsResolve() {
        assertEquals(EnumFacing.DOWN, GTUtility.enumByOrdinal(EnumFacing.VALUES, 0, EnumFacing.NORTH));
        assertEquals(EnumFacing.EAST, GTUtility.enumByOrdinal(EnumFacing.VALUES, 5, EnumFacing.NORTH));
    }

    @Test
    public void outOfRangeOrdinalsFallBack() {
        assertEquals(EnumFacing.NORTH, GTUtility.enumByOrdinal(EnumFacing.VALUES, 6, EnumFacing.NORTH));
        assertEquals(EnumFacing.NORTH, GTUtility.enumByOrdinal(EnumFacing.VALUES, -1, EnumFacing.NORTH));
    }
}

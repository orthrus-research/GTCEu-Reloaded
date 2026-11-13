package gregtech.api.worldgen.populator;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceIndicatorChunkTest {

    @Test
    void theGuaranteedIndicatorBelongsToTheCentersOwnChunk() {
        assertTrue(SurfaceRockPopulator.isInChunk(new BlockPos(40, 60, 17), 2, 1));
        assertFalse(SurfaceRockPopulator.isInChunk(new BlockPos(40, 60, 17), 1, 1));
        assertTrue(SurfaceRockPopulator.isInChunk(new BlockPos(-1, 60, -17), -1, -2));
        assertFalse(SurfaceRockPopulator.isInChunk(new BlockPos(-1, 60, -17), 0, -1));
        assertFalse(SurfaceRockPopulator.isInChunk(null, 0, 0));
    }
}

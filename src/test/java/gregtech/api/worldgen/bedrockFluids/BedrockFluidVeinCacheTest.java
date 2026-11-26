package gregtech.api.worldgen.bedrockFluids;

import gregtech.Bootstrap;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler.FluidVeinWorldEntry;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BedrockFluidVeinCacheTest {

    private int previousVersion;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void rememberVersion() {
        previousVersion = BedrockFluidVeinHandler.saveDataVersion;
    }

    @AfterEach
    void forgetVeins() {
        BedrockFluidVeinHandler.clearVeinCache();
        BedrockFluidVeinHandler.saveDataVersion = previousVersion;
    }

    @Test
    void theCacheIsReadOnlyOutsideItsHandlerAndClears() {
        ChunkPosDimension coords = new ChunkPosDimension(0, 1, 2);
        BedrockFluidVeinHandler.cacheVein(coords, new FluidVeinWorldEntry(null, 10, 20));

        Map<ChunkPosDimension, FluidVeinWorldEntry> view = BedrockFluidVeinHandler.getVeinCache();
        assertEquals(10, view.get(coords).getFluidYield());
        assertThrows(UnsupportedOperationException.class, view::clear);

        BedrockFluidVeinHandler.clearVeinCache();
        assertTrue(view.isEmpty());
    }

    @Test
    void readingAWorldsVeinDataReplacesThePreviousWorldsVeins() {
        ChunkPosDimension saved = new ChunkPosDimension(0, 3, 4);
        BedrockFluidVeinHandler.cacheVein(saved, new FluidVeinWorldEntry(null, 30, 40));
        NBTTagCompound nbt = new BedrockFluidVeinSaveData("test").writeToNBT(new NBTTagCompound());

        BedrockFluidVeinHandler.clearVeinCache();
        ChunkPosDimension stale = new ChunkPosDimension(-1, 5, 6);
        BedrockFluidVeinHandler.cacheVein(stale, new FluidVeinWorldEntry(null, 50, 60));
        new BedrockFluidVeinSaveData("test").readFromNBT(nbt);

        Map<ChunkPosDimension, FluidVeinWorldEntry> view = BedrockFluidVeinHandler.getVeinCache();
        assertEquals(1, view.size());
        assertEquals(40, view.get(saved).getOperationsRemaining());
    }
}

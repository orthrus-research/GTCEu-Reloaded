package gregtech.api.worldgen.bedrockFluids;

import gregtech.Bootstrap;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.world.chunk.Chunk;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BedrockFluidVeinRandomTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void chunkRandomMatchesTheChunkItReplaces() {
        DummyWorld world = DummyWorld.INSTANCE;
        for (int x = -40; x <= 40; x += 7) {
            for (int z = -40; z <= 40; z += 9) {
                assertEquals(new Chunk(world, x, z).getRandomWithSeed(90210).nextInt(),
                        BedrockFluidVeinHandler.chunkRandom(world.getSeed(), x, z, 90210).nextInt(),
                        "chunk " + x + ", " + z);
            }
        }
        // large coordinates overflow the int products the same way
        assertEquals(new Chunk(world, 3_750_000, -3_750_000).getRandomWithSeed(90210).nextInt(),
                BedrockFluidVeinHandler.chunkRandom(world.getSeed(), 3_750_000, -3_750_000, 90210).nextInt());
    }
}

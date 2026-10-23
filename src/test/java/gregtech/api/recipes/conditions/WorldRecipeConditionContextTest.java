package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldRecipeConditionContextTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void realWorldViewGuardsEveryStateAndBiomeReadAndNeverRequestsAChunk() {
        ConditionTestWorld world = new ConditionTestWorld();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(0, 64, 0);
        WorldRecipeConditionContext context = new WorldRecipeConditionContext(world, mutable);
        mutable.setPos(999, 1, 999);
        assertEquals(new BlockPos(0, 64, 0), context.position());
        world.unloaded.add(context.position().east());
        assertNull(context.blockAt(context.position().east()));
        assertEquals(0, world.reads);
        assertEquals(ConditionState.PASS, RecipeConditions.biome("minecraft:plains", false).test(context));
        assertEquals(ConditionState.UNAVAILABLE,
                RecipeConditions.adjacent(new String[][] { { "minecraft:stone" } }, true).test(context));
        assertEquals(18, world.reads, "One biome and 17 loaded neighboring states");
        world.unloaded.add(context.position());
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditions.biome("minecraft:plains", true).test(context));
        assertNull(context.biome());
        assertEquals(18, world.reads);
    }
}

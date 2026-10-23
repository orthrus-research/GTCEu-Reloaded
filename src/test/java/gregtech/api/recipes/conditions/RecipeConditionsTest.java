package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;

import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeConditionsTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void biomeHeightAndDaytimeUseIdentityInclusiveBoundsAndInversion() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        assertEquals(ConditionState.PASS, RecipeConditions.biome("minecraft:plains", false).test(context));
        assertEquals(ConditionState.FAIL, RecipeConditions.biome("minecraft:desert", false).test(context));
        assertEquals(ConditionState.PASS, RecipeConditions.biome("minecraft:desert", true).test(context));
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditions.biome("missing:biome", true).test(context));
        assertEquals(ConditionState.PASS, RecipeConditions.height(64, 64, false).test(context));
        context.pos = new BlockPos(0, 65, 0);
        assertEquals(ConditionState.FAIL, RecipeConditions.height(64, 64, false).test(context));
        assertEquals(ConditionState.PASS, RecipeConditions.height(64, 64, true).test(context));
        assertEquals(ConditionState.PASS, RecipeConditions.daytime(false).test(context));
        context.daytime = false;
        assertEquals(ConditionState.PASS, RecipeConditions.daytime(true).test(context));
        assertEquals(ConditionState.PASS, RecipeConditions.DAYTIME.create(false).test(context));
    }

    @Test
    void timeWindowsAreHalfOpenWrapAndHandleNegativeWorldTime() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        RecipeCondition<?> wrapped = RecipeConditions.time(23000, 1000, false);
        for (long tick : new long[] { 23000, 23999, 0, 999, -1, 48000 }) {
            context.time = tick;
            assertEquals(ConditionState.PASS, wrapped.test(context), "tick " + tick);
        }
        for (long tick : new long[] { 1000, 12000, 22999 }) {
            context.time = tick;
            assertEquals(ConditionState.FAIL, wrapped.test(context), "tick " + tick);
        }
        context.time = 6000;
        assertEquals(ConditionState.PASS, RecipeConditions.time(6000, 7000, false).test(context));
        context.time = 7000;
        assertEquals(ConditionState.FAIL, RecipeConditions.time(6000, 7000, false).test(context));
    }

    @Test
    void weatherUsesGlobalStrengthAndUnavailableNeverInvertsToPass() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        context.rain = 0.7f;
        context.thunder = 0.3f;
        assertEquals(ConditionState.PASS, RecipeConditions.rain(0.7f, false).test(context));
        assertEquals(ConditionState.FAIL, RecipeConditions.rain(0.8f, false).test(context));
        assertEquals(ConditionState.PASS, RecipeConditions.thunder(0.4f, true).test(context));
        assertTrue(context.reads.isEmpty(), "Weather must not imply sky/block checks");
        context.rain = Float.NaN;
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditions.rain(0.7f, true).test(context));
        context.available = false;
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditions.daytime(true).test(context));
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditions.height(0, 100, true).test(null));
    }

    @Test
    void parameterValidationRejectsInvalidNamesRangesWeatherAndMetadata() {
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.biome("Plains", false));
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.height(2, 1, false));
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.time(0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.time(-1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.time(0, 24000, false));
        for (float bad : new float[] { -0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> RecipeConditions.rain(bad, false));
            assertThrows(IllegalArgumentException.class, () -> RecipeConditions.thunder(bad, false));
        }
        assertThrows(IllegalArgumentException.class, () -> new BlockRequirement("minecraft:stone", 16));
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.adjacent(new String[0][], false));
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.adjacent(new String[][] { {} }, false));
    }

    @Test
    void adjacentUsesEighteenNonCornerNeighborsAndMetadata() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        RecipeCondition<?> condition = RecipeConditions.adjacent(new String[][] { { "minecraft:stone@1" } }, false);
        context.blocks.put(context.pos.add(1, 1, 1), Blocks.STONE.getStateFromMeta(1));
        assertEquals(ConditionState.FAIL, condition.test(context));
        assertEquals(18, context.reads.size());
        assertFalse(context.reads.contains(context.pos));
        assertFalse(context.reads.contains(context.pos.add(1, 1, 1)));
        context.blocks.put(context.pos.add(1, 1, 0), Blocks.STONE.getStateFromMeta(0));
        assertEquals(ConditionState.FAIL, condition.test(context));
        context.blocks.put(context.pos.add(1, 1, 0), Blocks.STONE.getStateFromMeta(1));
        assertEquals(ConditionState.PASS, condition.test(context));
    }

    @Test
    void adjacentReassignsOverlappingGroupsInsteadOfRejectingGreedily() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        context.blocks.put(context.pos.add(-1, -1, 0), Blocks.STONE.getDefaultState());
        context.blocks.put(context.pos.add(1, 1, 0), Blocks.DIRT.getDefaultState());
        RecipeCondition<?> condition = RecipeConditions.adjacent(new String[][] {
                { "minecraft:stone", "minecraft:dirt" }, { "minecraft:stone" } }, false);
        assertEquals(ConditionState.PASS, condition.test(context), "First group must move from stone to dirt");
        context.blocks.remove(context.pos.add(1, 1, 0));
        assertEquals(ConditionState.FAIL, condition.test(context), "One stone cannot serve two groups");
    }

    @Test
    void unloadedNeighborsOnlyBlockWhenLoadedEvidenceCannotCompleteMatching() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        context.unloaded.add(context.pos.east());
        RecipeCondition<?> condition = RecipeConditions.adjacent(new String[][] { { "minecraft:stone" } }, false);
        RecipeCondition<?> inverse = RecipeConditions.adjacent(new String[][] { { "minecraft:stone" } }, true);
        assertEquals(ConditionState.UNAVAILABLE, condition.test(context));
        assertEquals(ConditionState.UNAVAILABLE, inverse.test(context));
        context.blocks.put(context.pos.west(), Blocks.STONE.getDefaultState());
        assertEquals(ConditionState.PASS, condition.test(context));
        assertEquals(ConditionState.FAIL, inverse.test(context));
        assertEquals(ConditionState.UNAVAILABLE,
                RecipeConditions.adjacent(new String[][] { { "missing:block" } }, true).test(context));
    }

    @Test
    void sameTypeAlternativesRemainAndedBetweenSourceRecipesIncludingLaterEdits() {
        RecipeConditionSet first = RecipeConditionSet.EMPTY.add(RecipeConditions.biome("minecraft:plains", false))
                .add(RecipeConditions.biome("minecraft:desert", false));
        RecipeConditionSet second = RecipeConditionSet.EMPTY.add(RecipeConditions.biome("minecraft:desert", false))
                .add(RecipeConditions.biome("minecraft:forest", false));
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        RecipeConditionSet combined = first.and(second);
        assertEquals(2, combined.clauseCount());
        assertEquals(ConditionState.FAIL, combined.evaluate(context).state());
        context.biome = new ResourceLocation("minecraft", "desert");
        assertTrue(combined.evaluate(context).isSuccess());
        context.biome = new ResourceLocation("minecraft", "forest");
        assertFalse(combined.evaluate(context).isSuccess());
        RecipeConditionSet repeatedThenEdited = first.and(first).add(RecipeConditions.biome("minecraft:forest", false));
        assertFalse(repeatedThenEdited.evaluate(context).isSuccess(),
                "Appending identical source must retain its later constraint");
        assertEquals(ConditionState.FAIL,
                RecipeConditionSet.deserialize(combined.serialize()).evaluate(context).state());
        assertTrue(RecipeConditionSet.EMPTY.evaluate(null).isSuccess());
    }

    @Test
    void missingAdjacentAlternativesDoNotRejectACompleteKnownAssignment() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        context.blocks.put(context.pos.west(), Blocks.STONE.getDefaultState());
        String[][] alternatives = { { "minecraft:stone", "missing:block" } };
        RecipeCondition<?> condition = RecipeConditions.adjacent(alternatives, false);
        RecipeCondition<?> inverse = RecipeConditions.adjacent(alternatives, true);
        assertEquals(ConditionState.PASS, condition.test(context));
        assertEquals(ConditionState.FAIL, inverse.test(context));
        assertEquals(condition.serialize(), RecipeCondition.deserialize(condition.serialize()).serialize(),
                "Unresolved alternative names must remain in saved data");

        context.blocks.clear();
        assertEquals(ConditionState.UNAVAILABLE, condition.test(context));
        assertEquals(ConditionState.UNAVAILABLE, inverse.test(context));
        for (boolean inverted : new boolean[] { false, true }) {
            assertEquals(ConditionState.UNAVAILABLE,
                    RecipeConditions.adjacent(new String[][] { { "missing:block" } }, inverted).test(context),
                    "Unknown-only groups must not match the surrounding air");
            context.blocks.put(context.pos.west(), Blocks.STONE.getDefaultState());
            assertEquals(ConditionState.UNAVAILABLE, RecipeConditions.adjacent(new String[][] {
                    { "minecraft:stone", "missing:block" }, { "minecraft:stone" } }, inverted).test(context),
                    "One known neighbor cannot satisfy two groups despite an unresolved alternative");
            context.blocks.clear();
        }
    }

    @Test
    void repeatedAppendStoresResolvedGroupsWithoutAccumulatingSourceHistory() {
        RecipeConditionSet primary = RecipeConditionSet.EMPTY;
        for (int i = 0; i < 16; i++) primary = primary.add(RecipeConditions.height(i, 100, false));
        RecipeConditionSet combined = primary;
        for (int i = 0; i < 16; i++) {
            combined = combined.and(RecipeConditionSet.EMPTY.add(RecipeConditions.height(i, 100, false)));
        }
        // The primary plus 16 appended sources still has only 16 semantic AND groups.
        RecipeConditionSet copy = RecipeConditionSet.EMPTY.and(combined);
        for (int i = 0; i < 100; i++) copy = copy.and(combined);
        assertEquals(16, copy.clauseCount());
        assertEquals(copy.serialize(), RecipeConditionSet.deserialize(copy.serialize()).serialize());
        assertTrue(copy.evaluate(new TestRecipeConditionContext()).isSuccess());
    }
}

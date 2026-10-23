package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionFailureTransportTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void failedOrClauseRoundTripsThroughWidgetAndProbeAndDisplayEditsCannotMutateRecipeData() {
        RecipeConditionSet requirements = RecipeConditionSet.EMPTY
                .add(RecipeConditions.biome("minecraft:forest", false))
                .add(RecipeConditions.biome("minecraft:desert", false));
        NBTTagCompound recipeData = requirements.serialize();
        RecipeFailure failure = RecipeFailure.condition(requirements.evaluate(new TestRecipeConditionContext()));
        assertEquals(RecipeFailureReason.CONDITION, failure.getReason());
        assertEquals(2, failure.getConditionDescriptions().size());
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            failure.write(packet);
            assertEquals(failure, RecipeFailure.read(packet));
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
        NBTTagCompound probe = new NBTTagCompound();
        failure.writeToNBT(probe);
        assertEquals(failure, RecipeFailure.readFromNBT(probe));
        probe.getCompoundTag("ConditionDetails").getTagList("Alternatives", 10).getCompoundTagAt(0)
                .setString("Key", "changed.display_only");
        assertNotEquals(failure, RecipeFailure.readFromNBT(probe));
        assertEquals(recipeData, requirements.serialize());
        assertEquals(failure, RecipeFailure.condition(requirements.evaluate(new TestRecipeConditionContext())));
        assertThrows(UnsupportedOperationException.class, () -> failure.getConditionDescriptions().clear());
    }

    @Test
    void unavailableIsDistinctFromInvertedFailureAndMalformedDetailsKeepTheObservedBlockingReason() {
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        context.available = false;
        RecipeFailure unavailable = RecipeFailure.condition(RecipeConditionSet.EMPTY
                .add(RecipeConditions.daytime(true)).evaluate(context));
        assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE, unavailable.getReason());
        NBTTagCompound tag = new NBTTagCompound();
        unavailable.writeToNBT(tag);
        tag.setString("ConditionDetails", "wrong type");
        RecipeFailure malformed = RecipeFailure.readFromNBT(tag);
        assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE, malformed.getReason());
        assertTrue(malformed.getConditionDescriptions().isEmpty());
        assertFalse(malformed.isEmpty());
        assertEquals(RecipeFailureReason.CONDITION_UNAVAILABLE.getTranslationKey(),
                malformed.format((key, arguments) -> key));
        assertSame(RecipeFailure.NONE, RecipeFailure.condition(ConditionEvaluation.PASS));
    }

    @Test
    void reasonIdentifiersRemainStableAndLongObservedDescriptionsAreExplicitlyShortened() {
        assertEquals(24, RecipeFailureReason.PROGRESS_REQUIREMENTS.getId());
        assertEquals(25, RecipeFailureReason.CONDITION.getId());
        assertEquals(26, RecipeFailureReason.CONDITION_UNAVAILABLE.getId());
        RecipeConditionSet requirements = RecipeConditionSet.EMPTY;
        for (String biome : new String[] { "forest", "desert", "taiga", "swampland", "savanna", "birch_forest",
                "roofed_forest", "mesa" }) {
            requirements = requirements.add(RecipeConditions.biome("minecraft:" + biome, false));
        }
        RecipeFailure failure = RecipeFailure.condition(requirements.evaluate(new TestRecipeConditionContext()));
        String text = failure.format((key, arguments) -> key + java.util.Arrays.toString(arguments));
        assertTrue(text.endsWith("..."));
        assertEquals(8, failure.getConditionDescriptions().size());
        NBTTagCompound transport = new NBTTagCompound();
        failure.writeToNBT(transport);
        assertEquals(8, RecipeFailure.readFromNBT(transport).getConditionDescriptions().size());
    }
}

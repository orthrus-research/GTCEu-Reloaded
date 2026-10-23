package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RecipeConditionCodecTest {

    private static int nextType;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void builtinsRoundTripAndOwnAllCallerAndSerializedData() {
        String[][] blocks = { { "minecraft:stone", "minecraft:dirt" }, { "minecraft:planks@2" } };
        RecipeCondition<?> adjacent = RecipeConditions.adjacent(blocks, true);
        blocks[0][0] = "minecraft:air";
        for (RecipeCondition<?> condition : Arrays.asList(
                RecipeConditions.biome("minecraft:plains", false), RecipeConditions.height(1, 4, true),
                RecipeConditions.daytime(true), RecipeConditions.time(23000, 1000, false),
                RecipeConditions.rain(0.75f, true), RecipeConditions.thunder(0.5f, false), adjacent)) {
            NBTTagCompound source = condition.serialize();
            RecipeCondition<?> restored = RecipeCondition.deserialize(source);
            assertEquals(condition, restored);
            source.setString("Type", "missing:mutated");
            assertEquals(condition, restored);
            NBTTagCompound returned = restored.serialize();
            returned.getCompoundTag("Data").setString("Mutation", "outside");
            assertEquals(condition.serialize(), restored.serialize());
            assertEquals(condition.description(), restored.description());
        }
        assertTrue(adjacent.serialize().toString().contains("minecraft:stone"));
        assertThrows(UnsupportedOperationException.class, () -> adjacent.description().arguments().add("outside"));
    }

    @Test
    void unknownAndMalformedLeavesStayBlockingUnderInversionAndPreserveSmallPayloads() {
        NBTTagCompound unknown = RecipeConditions.biome("minecraft:plains", true).serialize();
        unknown.setString("Type", "missing:condition");
        unknown.getCompoundTag("Data").setString("AddonValue", "retained");
        RecipeCondition<?> condition = RecipeCondition.deserialize(unknown);
        assertEquals(unknown, condition.serialize());
        assertEquals(ConditionState.UNAVAILABLE, condition.test(new TestRecipeConditionContext()));
        RecipeConditionSet set = RecipeConditionSet.EMPTY.add(condition)
                .add(RecipeConditions.biome("minecraft:plains", false));
        assertEquals(ConditionState.UNAVAILABLE, set.evaluate(new TestRecipeConditionContext()).state());
        assertEquals(set.serialize(), RecipeConditionSet.deserialize(set.serialize()).serialize());
        assertFalse(
                RecipeConditionSet.deserialize(set.serialize()).evaluate(new TestRecipeConditionContext()).isSuccess());

        for (String field : new String[] { "Version", "Inverted", "Data", "Type" }) {
            NBTTagCompound malformed = RecipeConditions.daytime(false).serialize();
            malformed.setString(field, "wrong tag type");
            RecipeCondition<?> result = RecipeCondition.deserialize(malformed);
            assertEquals(ConditionState.UNAVAILABLE, result.test(new TestRecipeConditionContext()));
            assertEquals(malformed, result.serialize());
        }
        NBTTagCompound future = RecipeConditions.daytime(false).serialize();
        future.setInteger("Version", 2);
        assertEquals(ConditionState.UNAVAILABLE,
                RecipeCondition.deserialize(future).test(new TestRecipeConditionContext()));
        future.setInteger("Version", 1);
        future.setByte("Inverted", (byte) 2);
        assertEquals(ConditionState.UNAVAILABLE,
                RecipeCondition.deserialize(future).test(new TestRecipeConditionContext()));
    }

    @Test
    void oversizedDeepWideAndArrayPayloadsBecomeSmallUnavailableMarkersBeforeCopy() {
        NBTTagCompound huge = unknown();
        char[] text = new char[20000];
        Arrays.fill(text, 'x');
        huge.getCompoundTag("Data").setString("Huge", new String(text));
        assertSmallMarker(huge);
        NBTTagCompound deep = unknown();
        NBTTagCompound cursor = deep.getCompoundTag("Data");
        for (int i = 0; i < 1000; i++) {
            NBTTagCompound child = new NBTTagCompound();
            cursor.setTag("Child", child);
            cursor = child;
        }
        assertSmallMarker(deep);
        NBTTagCompound wide = unknown();
        for (int i = 0; i < 1000; i++) wide.getCompoundTag("Data").setInteger("Entry" + i, i);
        assertSmallMarker(wide);
        NBTTagCompound array = unknown();
        array.getCompoundTag("Data").setByteArray("Array", new byte[2]);
        assertSmallMarker(array);
        assertEquals(ConditionState.UNAVAILABLE, RecipeCondition.deserialize(new NBTTagInt(1)).test(null));
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditionSet.deserialize(deep).evaluate(null).state());
    }

    @Test
    void malformedSetAndUnknownVersionNeverDeserializeAsEmptyRequirements() {
        NBTTagCompound set = RecipeConditionSet.EMPTY.serialize();
        set.setInteger("Version", 20);
        RecipeConditionSet unknown = RecipeConditionSet.deserialize(set);
        assertFalse(unknown.isEmpty());
        assertEquals(ConditionState.UNAVAILABLE, unknown.evaluate(null).state());
        assertEquals(unknown.serialize(), RecipeConditionSet.deserialize(unknown.serialize()).serialize());
        set = RecipeConditionSet.EMPTY.serialize();
        NBTTagList wrongEntries = new NBTTagList();
        wrongEntries.appendTag(new NBTTagInt(1));
        set.setTag("Primary", wrongEntries);
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditionSet.deserialize(set).evaluate(null).state());
        assertEquals(ConditionState.UNAVAILABLE, RecipeConditionSet.deserialize(null).evaluate(null).state());
    }

    @Test
    void versionOnePerSourceListsNormalizeWithoutChangingTheirLogicalRequirements() {
        NBTTagCompound envelope = RecipeConditionSet.EMPTY.serialize();
        NBTTagList conditions = new NBTTagList();
        conditions.appendTag(RecipeConditions.biome("minecraft:plains", false).serialize());
        conditions.appendTag(RecipeConditions.biome("minecraft:desert", false).serialize());
        conditions.appendTag(RecipeConditions.height(70, 90, false).serialize());
        NBTTagCompound source = new NBTTagCompound();
        source.setTag("Conditions", conditions);
        NBTTagList appended = new NBTTagList();
        appended.appendTag(source);
        envelope.setTag("Appended", appended);
        NBTTagCompound original = envelope.copy();
        RecipeConditionSet normalized = RecipeConditionSet.deserialize(envelope);
        assertEquals(2, normalized.clauseCount());
        TestRecipeConditionContext context = new TestRecipeConditionContext();
        assertFalse(normalized.evaluate(context).isSuccess());
        context.pos = new net.minecraft.util.math.BlockPos(0, 80, 0);
        assertTrue(normalized.evaluate(context).isSuccess());
        context.biome = new ResourceLocation("minecraft", "forest");
        assertFalse(normalized.evaluate(context).isSuccess());
        assertEquals(original, envelope);
        assertEquals(normalized.serialize(), RecipeConditionSet.deserialize(normalized.serialize()).serialize());
    }

    @Test
    void adapterErrorsStayUnavailableAndRegistrationRejectsDuplicates() {
        ResourceLocation id = new ResourceLocation("condition_test", "adapter_" + nextType++);
        RecipeConditionType<Integer> type = integerType(id, true);
        assertThrows(IllegalArgumentException.class, () -> RecipeConditions.register(type));
        RecipeCondition<?> first = type.create(1, true);
        assertEquals(ConditionState.UNAVAILABLE, first.test(new TestRecipeConditionContext()));
        assertEquals(first, RecipeCondition.deserialize(first.serialize()));
        assertEquals(ConditionState.UNAVAILABLE, first.test(null));
    }

    @Test
    void leafClauseAndAlternativeBoundsRejectInsteadOfTruncating() {
        RecipeConditionSet ordinary = RecipeConditionSet.EMPTY;
        for (int i = 0; i < 16; i++) ordinary = ordinary.add(RecipeConditions.height(i, 100, false));
        RecipeConditionSet full = ordinary;
        assertThrows(IllegalArgumentException.class, () -> full.add(RecipeConditions.height(16, 100, false)));
        RecipeConditionSet alternatives = RecipeConditionSet.EMPTY;
        RecipeConditionType<Integer> type = integerType(new ResourceLocation("condition_test", "limits_" + nextType++),
                true);
        for (int i = 0; i < 8; i++) alternatives = alternatives.add(type.create(i));
        RecipeConditionSet fullAlternatives = alternatives;
        assertThrows(IllegalArgumentException.class, () -> fullAlternatives.add(type.create(8)));
        RecipeConditionSet leaves = RecipeConditionSet.EMPTY;
        RecipeConditionType<Integer> last = null;
        for (int group = 0; group < 16; group++) {
            last = integerType(new ResourceLocation("condition_test", "leaves_" + nextType++), true);
            leaves = leaves.add(last.create(0)).add(last.create(1));
        }
        RecipeConditionSet fullLeaves = leaves;
        RecipeCondition<?> extra = last.create(2);
        assertThrows(IllegalArgumentException.class, () -> fullLeaves.add(extra));
        assertEquals(16, RecipeConditionSet.deserialize(fullLeaves.serialize()).clauseCount());
    }

    private static RecipeConditionType<Integer> integerType(ResourceLocation id, boolean alternatives) {
        return RecipeConditions
                .register(new RecipeConditionType<>(id, alternatives, tag -> tag.getInteger("Value"), value -> {
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setInteger("Value", value);
                    return tag;
                }, (value, context) -> { throw new IllegalStateException("Unavailable addon evidence"); },
                        value -> new ConditionDescription("condition_test.value", value.toString())));
    }

    private static NBTTagCompound unknown() {
        NBTTagCompound unknown = RecipeConditions.daytime(true).serialize();
        unknown.setString("Type", "missing:type");
        return unknown;
    }

    private static void assertSmallMarker(NBTTagCompound input) {
        RecipeCondition<?> marker = RecipeCondition.deserialize(input);
        assertEquals("gregtech:invalid_condition", marker.typeId());
        assertEquals(ConditionState.UNAVAILABLE, marker.test(null));
        assertTrue(marker.serialize().toString().length() < 200);
    }
}

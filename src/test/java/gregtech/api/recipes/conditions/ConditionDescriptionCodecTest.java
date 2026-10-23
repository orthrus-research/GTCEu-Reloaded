package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionDescriptionCodecTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void observedDescriptionsRoundTripWithoutResolvingUnknownTypesOrRetainingMutableData() {
        String[] callerArguments = { "minecraft:plains", "1" };
        ConditionDescription unknown = new ConditionDescription("missing:adapter", "missing.description", true,
                callerArguments);
        callerArguments[0] = "mutated";
        List<ConditionDescription> descriptions = Arrays.asList(RecipeConditions.height(4, 20, false).description(),
                unknown);
        assertEquals("gregtech:height", descriptions.get(0).typeId());
        NBTTagCompound tag = ConditionDescriptionCodec.serialize(descriptions);
        List<ConditionDescription> restored = ConditionDescriptionCodec.deserialize(tag);
        assertEquals(descriptions, restored);
        tag.getTagList("Alternatives", 10).getCompoundTagAt(0).setString("Key", "mutated");
        assertEquals(descriptions, restored);
        assertEquals("minecraft:plains", unknown.arguments().get(0));
        assertThrows(UnsupportedOperationException.class, () -> restored.add(unknown));
        assertThrows(UnsupportedOperationException.class, () -> restored.get(0).arguments().add("mutated"));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            ConditionDescriptionCodec.write(buffer, descriptions);
            assertEquals(descriptions, ConditionDescriptionCodec.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
        assertEquals("not(missing.description[minecraft:plains, 1])",
                unknown.format((key, args) -> key.equals("gregtech.recipe.condition.not") ? "not(" + args[0] + ")" :
                        key + Arrays.toString(args)));
    }

    @Test
    void maximumAlternativeAndArgumentPayloadFitsBothBoundedTransports() {
        String[] arguments = new String[ConditionDescription.MAX_ARGUMENTS];
        Arrays.fill(arguments, repeat('\u754c', ConditionDescription.MAX_TEXT_LENGTH));
        List<ConditionDescription> descriptions = new ArrayList<>();
        for (int i = 0; i < RecipeConditionSet.MAX_ALTERNATIVES; i++) {
            descriptions.add(new ConditionDescription("test:" + repeat('a', 123), repeat('k', 128),
                    (i & 1) != 0, arguments));
        }
        assertEquals(descriptions,
                ConditionDescriptionCodec.deserialize(ConditionDescriptionCodec.serialize(descriptions)));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            ConditionDescriptionCodec.write(buffer, descriptions);
            assertTrue(buffer.readableBytes() < 65536);
            assertEquals(descriptions, ConditionDescriptionCodec.read(buffer));
            assertEquals(0, buffer.readableBytes());
            descriptions.add(descriptions.get(0));
            assertThrows(IllegalArgumentException.class, () -> ConditionDescriptionCodec.write(buffer, descriptions));
            assertEquals(0, buffer.readableBytes(), "Invalid writer input must fail before adding any bytes");
            assertThrows(IllegalArgumentException.class, () -> ConditionDescriptionCodec.serialize(descriptions));
        } finally {
            buffer.release();
        }
    }

    @Test
    void malformedNbtNeverProducesPartialDiagnosticDetails() {
        List<ConditionDescription> descriptions = Arrays.asList(RecipeConditions.daytime(false).description(),
                RecipeConditions.height(0, 100, true).description());
        for (String field : new String[] { "Version", "Alternatives" }) {
            NBTTagCompound tag = ConditionDescriptionCodec.serialize(descriptions);
            tag.setString(field, "wrong-type");
            assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty());
        }
        for (String field : new String[] { "Type", "Key", "Inverted", "Arguments" }) {
            NBTTagCompound tag = ConditionDescriptionCodec.serialize(descriptions);
            tag.getTagList("Alternatives", 10).getCompoundTagAt(1).setTag(field, new NBTTagInt(2));
            assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty(), field);
        }
        NBTTagCompound tag = ConditionDescriptionCodec.serialize(descriptions);
        NBTTagCompound second = tag.getTagList("Alternatives", 10).getCompoundTagAt(1);
        second.setByte("Inverted", (byte) 2);
        assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty());
        second.setBoolean("Inverted", false);
        second.setString("Type", "missing_namespace");
        assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty());
        second.setString("Type", "missing:adapter");
        second.setString("Key", repeat('x', 129));
        assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty());
        second.setString("Key", "valid.key");
        NBTTagList arguments = new NBTTagList();
        for (int i = 0; i < 9; i++) arguments.appendTag(new NBTTagString("value"));
        second.setTag("Arguments", arguments);
        assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty());
        second.setTag("Arguments", new NBTTagList());
        second.setByteArray("UnexpectedArray", new byte[65536]);
        assertTrue(ConditionDescriptionCodec.deserialize(tag).isEmpty());
        NBTTagCompound deep = new NBTTagCompound();
        NBTTagCompound current = deep;
        for (int i = 0; i < 1000; i++) {
            NBTTagCompound child = new NBTTagCompound();
            current.setTag("Nested", child);
            current = child;
        }
        assertTrue(ConditionDescriptionCodec.deserialize(deep).isEmpty());
        assertTrue(ConditionDescriptionCodec.deserialize(null).isEmpty());
    }

    @Test
    void truncatedAndOverdeclaredPacketsReturnNoPartialDetails() {
        PacketBuffer complete = new PacketBuffer(Unpooled.buffer());
        try {
            ConditionDescriptionCodec.write(complete, Arrays.asList(RecipeConditions.daytime(false).description(),
                    RecipeConditions.biome("minecraft:plains", true).description()));
            for (int length = 0; length < complete.readableBytes(); length++) {
                PacketBuffer truncated = new PacketBuffer(complete.copy(0, length));
                try {
                    assertTrue(ConditionDescriptionCodec.read(truncated).isEmpty(), "Prefix length " + length);
                } finally {
                    truncated.release();
                }
            }
        } finally {
            complete.release();
        }
        for (int count : new int[] { -1, 9, Integer.MAX_VALUE }) {
            PacketBuffer invalid = new PacketBuffer(Unpooled.buffer());
            try {
                invalid.writeVarInt(1);
                invalid.writeVarInt(count);
                assertTrue(ConditionDescriptionCodec.read(invalid).isEmpty());
            } finally {
                invalid.release();
            }
        }
        PacketBuffer invalid = new PacketBuffer(Unpooled.buffer());
        try {
            invalid.writeVarInt(1);
            invalid.writeVarInt(1);
            invalid.writeVarInt(Integer.MAX_VALUE);
            assertTrue(ConditionDescriptionCodec.read(invalid).isEmpty());
            invalid.clear();
            ConditionDescriptionCodec.write(invalid, Collections.emptyList());
            assertTrue(ConditionDescriptionCodec.read(invalid).isEmpty());
            assertEquals(0, invalid.readableBytes());
        } finally {
            invalid.release();
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}

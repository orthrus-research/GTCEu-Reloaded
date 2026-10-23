package gregtech.api.recipes.conditions;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded observed diagnostic data only. Reading never resolves adapters or evaluates conditions. */
public final class ConditionDescriptionCodec {

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 65536;

    private ConditionDescriptionCodec() {}

    public static NBTTagCompound serialize(List<ConditionDescription> alternatives) {
        requireCount(alternatives.size());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", VERSION);
        NBTTagList entries = new NBTTagList();
        for (ConditionDescription description : alternatives) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Type", description.typeId());
            entry.setString("Key", description.key());
            entry.setBoolean("Inverted", description.isInverted());
            NBTTagList arguments = new NBTTagList();
            for (String argument : description.arguments()) arguments.appendTag(new NBTTagString(argument));
            entry.setTag("Arguments", arguments);
            entries.appendTag(entry);
        }
        tag.setTag("Alternatives", entries);
        return tag;
    }

    /** Malformed descriptions omit details; the containing observed failure reason remains authoritative. */
    public static List<ConditionDescription> deserialize(NBTBase input) {
        if (!(input instanceof NBTTagCompound) || !ConditionPayloads.bounded(input, 5, MAX_BYTES, 128)) {
            return Collections.emptyList();
        }
        try {
            NBTTagCompound tag = (NBTTagCompound) input;
            ConditionPayloads.requireKeys(tag, "Version", "Alternatives");
            ConditionPayloads.requireTag(tag, "Version", 3);
            ConditionPayloads.requireTag(tag, "Alternatives", 9);
            if (tag.getInteger("Version") != VERSION) return Collections.emptyList();
            NBTTagList entries = (NBTTagList) tag.getTag("Alternatives");
            requireCount(entries.tagCount());
            if (entries.tagCount() > 0 && entries.getTagType() != 10) return Collections.emptyList();
            List<ConditionDescription> descriptions = new ArrayList<>();
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                ConditionPayloads.requireKeys(entry, "Type", "Key", "Inverted", "Arguments");
                ConditionPayloads.requireTag(entry, "Type", 8);
                ConditionPayloads.requireTag(entry, "Key", 8);
                ConditionPayloads.requireTag(entry, "Inverted", 1);
                ConditionPayloads.requireTag(entry, "Arguments", 9);
                byte inverted = entry.getByte("Inverted");
                if (inverted != 0 && inverted != 1) return Collections.emptyList();
                NBTTagList arguments = (NBTTagList) entry.getTag("Arguments");
                if (arguments.tagCount() > ConditionDescription.MAX_ARGUMENTS ||
                        (arguments.tagCount() > 0 && arguments.getTagType() != 8))
                    return Collections.emptyList();
                String[] values = new String[arguments.tagCount()];
                for (int j = 0; j < values.length; j++) values[j] = arguments.getStringTagAt(j);
                descriptions.add(new ConditionDescription(entry.getString("Type"), entry.getString("Key"),
                        inverted == 1, values));
            }
            return Collections.unmodifiableList(descriptions);
        } catch (RuntimeException invalid) {
            return Collections.emptyList();
        }
    }

    public static void write(PacketBuffer buffer, List<ConditionDescription> alternatives) {
        requireCount(alternatives.size());
        buffer.writeVarInt(VERSION);
        buffer.writeVarInt(alternatives.size());
        for (ConditionDescription description : alternatives) {
            buffer.writeString(description.typeId());
            buffer.writeString(description.key());
            buffer.writeBoolean(description.isInverted());
            buffer.writeVarInt(description.arguments().size());
            for (String argument : description.arguments()) buffer.writeString(argument);
        }
    }

    public static List<ConditionDescription> read(PacketBuffer buffer) {
        try {
            if (buffer.readVarInt() != VERSION) return Collections.emptyList();
            int count = buffer.readVarInt();
            requireCount(count);
            List<ConditionDescription> descriptions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String type = buffer.readString(ConditionDescription.MAX_TEXT_LENGTH);
                String key = buffer.readString(ConditionDescription.MAX_TEXT_LENGTH);
                int inverted = buffer.readUnsignedByte();
                if (inverted > 1) return Collections.emptyList();
                int countArguments = buffer.readVarInt();
                if (countArguments < 0 || countArguments > ConditionDescription.MAX_ARGUMENTS) {
                    return Collections.emptyList();
                }
                String[] arguments = new String[countArguments];
                for (int j = 0; j < arguments.length; j++) {
                    arguments[j] = buffer.readString(ConditionDescription.MAX_TEXT_LENGTH);
                }
                descriptions.add(new ConditionDescription(type, key, inverted == 1, arguments));
            }
            return Collections.unmodifiableList(descriptions);
        } catch (RuntimeException invalid) {
            return Collections.emptyList();
        }
    }

    private static void requireCount(int count) {
        if (count < 0 || count > RecipeConditionSet.MAX_ALTERNATIVES) {
            throw new IllegalArgumentException("Diagnostic alternatives exceed their bounds");
        }
    }
}

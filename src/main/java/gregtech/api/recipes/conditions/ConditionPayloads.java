package gregtech.api.recipes.conditions;

import net.minecraft.nbt.*;

import java.util.Set;

/** Validate before copying or decoding untrusted condition NBT. Bounds include unknown descriptors. */
final class ConditionPayloads {

    static final int MAX_DEPTH = 8;
    static final int MAX_BYTES = 16384;
    static final int MAX_NODES = 512;

    private ConditionPayloads() {}

    static boolean bounded(NBTBase value) {
        return bounded(value, MAX_DEPTH, MAX_BYTES, MAX_NODES);
    }

    static boolean bounded(NBTBase value, int depth, int bytes, int nodes) {
        return value != null && measure(value, 0, depth, new int[] { bytes, nodes });
    }

    private static boolean measure(NBTBase value, int depth, int maximumDepth, int[] remaining) {
        if (depth > maximumDepth || --remaining[1] < 0 || (remaining[0] -= 16) < 0) return false;
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) value;
            Set<String> keys = compound.getKeySet();
            if (keys.size() > remaining[1]) return false;
            for (String key : keys) {
                if (key.length() > 128 || (remaining[0] -= key.length() * 3) < 0 ||
                        !measure(compound.getTag(key), depth + 1, maximumDepth, remaining))
                    return false;
            }
        } else if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;
            if (list.tagCount() > remaining[1]) return false;
            for (int i = 0; i < list.tagCount(); i++) {
                if (!measure(list.get(i), depth + 1, maximumDepth, remaining)) return false;
            }
        } else if (value instanceof NBTTagString) {
            int length = ((NBTTagString) value).getString().length();
            if (length > MAX_BYTES / 3 || (remaining[0] -= length * 3) < 0) return false;
        } else if (value.getId() < 1 || value.getId() > 6) {
            // No built-in condition uses arrays. Unknown array payloads become a bounded unavailable marker.
            return false;
        }
        return true;
    }

    static void requireKeys(NBTTagCompound data, String... keys) {
        if (data.getKeySet().size() != keys.length) throw new IllegalArgumentException("Unexpected condition fields");
        for (String key : keys)
            if (!data.hasKey(key)) throw new IllegalArgumentException("Missing condition field: " + key);
    }

    static void requireTag(NBTTagCompound data, String key, int type) {
        if (!data.hasKey(key, type)) throw new IllegalArgumentException("Wrong condition field type: " + key);
    }
}

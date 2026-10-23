package gregtech.api.configuration;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.Arrays;
import java.util.HashSet;

/** Strict scalar decoding: missing keys, coercions, unknown keys and out-of-range values are rejected. */
public final class ConfigurationData {

    private ConfigurationData() {}

    public static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Unsupported or invalid configuration");
    }

    public static void keys(NBTTagCompound data, String... keys) {
        require(data.getKeySet().equals(new HashSet<>(Arrays.asList(keys))));
    }

    public static int integer(NBTTagCompound data, String key, int minimum, int maximum) {
        require(data.hasKey(key, NBT.TAG_INT));
        int value = data.getInteger(key);
        require(value >= minimum && value <= maximum);
        return value;
    }

    public static boolean bool(NBTTagCompound data, String key) {
        require(data.hasKey(key, NBT.TAG_BYTE));
        byte value = data.getByte(key);
        require(value == 0 || value == 1);
        return value == 1;
    }

    public static String string(NBTTagCompound data, String key) {
        require(data.hasKey(key, NBT.TAG_STRING));
        String value = data.getString(key);
        require(!value.isEmpty() && value.length() <= 128);
        return value;
    }

    public static NBTTagCompound compound(NBTTagCompound data, String key) {
        require(data.hasKey(key, NBT.TAG_COMPOUND));
        return data.getCompoundTag(key);
    }

    public static EnumFacing facing(NBTTagCompound data, String key) {
        return EnumFacing.VALUES[integer(data, key, 0, 5)];
    }
}

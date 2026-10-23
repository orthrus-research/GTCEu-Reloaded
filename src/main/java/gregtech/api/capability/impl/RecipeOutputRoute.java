package gregtech.api.capability.impl;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

/** Saved routing metadata only: running outputs never retain a world handler or an input-batch pointer. */
final class RecipeOutputRoute {

    static final RecipeOutputRoute LEGACY = new RecipeOutputRoute(false, -1, true);
    static final RecipeOutputRoute UNAVAILABLE = new RecipeOutputRoute(false, -1, false);
    final boolean painted;
    final int color;
    final boolean valid;

    private RecipeOutputRoute(boolean painted, int color, boolean valid) {
        this.painted = painted;
        this.color = color;
        this.valid = valid;
    }

    static RecipeOutputRoute painted(int color) {
        return color < -1 || color > 0xFFFFFF ? UNAVAILABLE : new RecipeOutputRoute(true, color, true);
    }

    NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", valid ? 1 : -1);
        tag.setBoolean("Painted", painted);
        tag.setInteger("Color", color);
        return tag;
    }

    static RecipeOutputRoute deserialize(NBTBase value) {
        if (!(value instanceof NBTTagCompound tag) || tag.getKeySet().size() != 3 ||
                !tag.hasKey("Version", 3) || tag.getInteger("Version") != 1 || !tag.hasKey("Painted", 1) ||
                (tag.getByte("Painted") != 0 && tag.getByte("Painted") != 1) || !tag.hasKey("Color", 3))
            return UNAVAILABLE;
        int color = tag.getInteger("Color");
        if (!tag.getBoolean("Painted")) return color == -1 ? LEGACY : UNAVAILABLE;
        return painted(color);
    }
}

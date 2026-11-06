package gregtech.api.recipes.definition;

import net.minecraft.nbt.NBTTagCompound;

enum AnyNbt implements NbtPolicy {

    INSTANCE;

    @Override
    public boolean matches(NBTTagCompound tag) {
        return true;
    }
}

package gregtech.api.recipes.definition;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

final class ContainsNbt implements NbtPolicy {

    private final NBTTagCompound required;

    ContainsNbt(NBTTagCompound required) {
        this.required = Objects.requireNonNull(required, "Required NBT").copy();
    }

    @Override
    public boolean matches(NBTTagCompound tag) {
        if (required.isEmpty()) return true;
        if (tag == null) return false;
        for (String key : required.getKeySet()) {
            if (!Objects.equals(required.getTag(key), tag.getTag(key))) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContainsNbt policy && required.equals(policy.required);
    }

    @Override
    public int hashCode() {
        return required.hashCode();
    }
}

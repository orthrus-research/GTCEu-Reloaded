package gregtech.api.recipes.definition;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

final class ExactNbt implements NbtPolicy {

    private final NBTTagCompound required;

    ExactNbt(NBTTagCompound required) {
        this.required = required == null ? null : required.copy();
    }

    @Override
    public boolean matches(NBTTagCompound tag) {
        return Objects.equals(required, tag);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExactNbt policy && Objects.equals(required, policy.required);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(required);
    }
}

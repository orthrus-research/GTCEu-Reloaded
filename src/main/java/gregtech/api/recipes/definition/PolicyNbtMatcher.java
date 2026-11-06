package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

record PolicyNbtMatcher(NbtPolicy policy) implements NBTMatcher {

    PolicyNbtMatcher {
        Objects.requireNonNull(policy, "NBT policy");
    }

    @Override
    public boolean evaluate(NBTTagCompound tag, NBTCondition ignored) {
        return policy.matches(tag);
    }
}

package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;

import net.minecraft.nbt.NBTTagCompound;

/** An immutable NBT matching policy shared by Java and scripting frontends. */
public sealed interface NbtPolicy permits AnyNbt, ExactNbt, ContainsNbt {

    static NbtPolicy any() {
        return AnyNbt.INSTANCE;
    }

    /** Matches the complete tag, including the distinction between absent and empty tags. */
    static NbtPolicy exact(NBTTagCompound tag) {
        return new ExactNbt(tag);
    }

    /** Requires top-level entries; nested compounds and lists retain exact value semantics. */
    static NbtPolicy contains(NBTTagCompound tag) {
        return new ContainsNbt(tag);
    }

    boolean matches(NBTTagCompound tag);

    default NBTMatcher compile() {
        return new PolicyNbtMatcher(this);
    }
}

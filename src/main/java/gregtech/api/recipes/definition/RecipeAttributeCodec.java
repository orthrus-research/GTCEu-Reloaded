package gregtech.api.recipes.definition;

import net.minecraft.nbt.NBTBase;

/** Registration-time value codec. Decode must reject unknown versions, invalid types and invalid values. */
public interface RecipeAttributeCodec<V> {

    NBTBase encode(V value);

    V decode(NBTBase payload);
}

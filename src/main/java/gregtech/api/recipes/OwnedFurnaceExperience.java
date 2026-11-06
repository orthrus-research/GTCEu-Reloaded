package gregtech.api.recipes;

import gregtech.api.recipes.definition.ItemValue;

import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.Objects;

record OwnedFurnaceExperience(ItemStack key, ItemValue snapshot, float amount) {

    OwnedFurnaceExperience(ItemStack key, float amount) {
        this(key, ItemValue.of(key), amount);
    }

    void verify(Map<ItemStack, Float> experience) {
        if (!Objects.equals(experience.get(key), amount) || !snapshot.equals(ItemValue.of(key))) {
            throw new IllegalStateException("Owned furnace experience changed outside its registration group");
        }
    }
}

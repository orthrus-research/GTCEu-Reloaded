package gregtech.api.recipes.definition;

import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

record ItemPolicyMatcher(List<ItemValue> alternatives, NbtPolicy nbt, CapabilityPolicy capabilities)
        implements NBTMatcher {

    ItemPolicyMatcher {
        alternatives = List.copyOf(alternatives);
    }

    @Override
    public boolean evaluate(NBTTagCompound tag, NBTCondition condition) {
        return nbt.matches(tag);
    }

    @Override
    public boolean evaluate(ItemStack stack, NBTCondition condition) {
        if (!nbt.matches(stack.getTagCompound())) return false;
        if (capabilities == CapabilityPolicy.IGNORE) return true;
        for (ItemValue alternative : alternatives) {
            if (alternative.matchesCapabilities(stack)) return true;
        }
        return false;
    }
}

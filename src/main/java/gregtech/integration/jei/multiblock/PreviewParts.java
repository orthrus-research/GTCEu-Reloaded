package gregtech.integration.jei.multiblock;

import gregtech.api.util.ItemStackHashStrategy;

import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class PreviewParts {

    private PreviewParts() {}

    static List<ItemStack> count(Collection<ItemStack> stacks) {
        Object2IntOpenCustomHashMap<ItemStack> amounts = new Object2IntOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        List<ItemStack> order = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (!amounts.containsKey(stack)) order.add(stack.copy());
            amounts.addTo(stack.copy(), stack.getCount());
        }
        for (ItemStack stack : order) stack.setCount(amounts.getInt(stack));
        return order;
    }

    static List<ItemStack> page(List<ItemStack> parts, int page, int pageSize) {
        List<ItemStack> result = new ArrayList<>();
        int start = Math.max(0, page) * pageSize;
        for (int i = start; i < Math.min(parts.size(), start + pageSize); i++) result.add(parts.get(i).copy());
        return result;
    }
}

package gregtech.api.util;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Carries items that an inconsistent handler would otherwise discard during rollback. */
public class ItemTransferException extends IllegalStateException {

    private static final long serialVersionUID = 1L;
    private final List<ItemStack> remainders;

    public ItemTransferException(List<ItemStack> remainders) {
        super("The destination rejected admitted items and the source refused their return");
        this.remainders = copy(remainders);
    }

    public List<ItemStack> getRemainders() {
        return copy(remainders);
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) result.add(stack.copy());
        return Collections.unmodifiableList(result);
    }
}

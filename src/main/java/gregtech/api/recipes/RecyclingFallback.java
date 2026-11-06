package gregtech.api.recipes;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;

import java.util.List;
import java.util.Objects;

/** Native fallback inputs observed while resolving a composition graph, including direct prefix-list edits. */
record RecyclingFallback(ItemAndMetadata input, MaterialStack primary, List<MaterialStack> secondary) {

    static RecyclingFallback capture(ItemAndMetadata input) {
        var stack = input.toItemStack();
        var prefix = OreDictUnifier.getPrefix(stack);
        return new RecyclingFallback(input, OreDictUnifier.getMaterial(stack),
                prefix == null ? List.of() : List.copyOf(prefix.secondaryMaterials));
    }

    boolean isCurrent() {
        var stack = input.toItemStack();
        var prefix = OreDictUnifier.getPrefix(stack);
        return Objects.equals(primary, OreDictUnifier.getMaterial(stack)) &&
                secondary.equals(prefix == null ? List.of() : prefix.secondaryMaterials);
    }
}

package gregtech.integration.jei.multiblock;

import gregtech.api.pattern.TraceabilityPredicate;

import net.minecraft.item.ItemStack;

final class PreviewCandidate {

    final TraceabilityPredicate.SimplePredicate predicate;
    final PreviewBlock block;

    PreviewCandidate(TraceabilityPredicate.SimplePredicate predicate, PreviewBlock block) {
        this.predicate = predicate;
        this.block = block;
    }

    ItemStack stack() {
        return block.stack();
    }
}

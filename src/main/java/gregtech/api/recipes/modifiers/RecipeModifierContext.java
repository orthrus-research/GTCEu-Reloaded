package gregtech.api.recipes.modifiers;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** Values captured for one startup attempt. Does not expose a World, controller or mutable inventory. */
public final class RecipeModifierContext {

    private final ResourceLocation machineId;
    private final int dimension;
    private final BlockPos position;
    private final long maximumVoltage;
    private final int parallelLimit;

    public RecipeModifierContext(ResourceLocation machineId, int dimension, BlockPos position, long maximumVoltage,
                                 int parallelLimit) {
        if (maximumVoltage < 0 || parallelLimit < 1) throw new IllegalArgumentException("Invalid machine limits");
        this.machineId = Objects.requireNonNull(machineId);
        this.dimension = dimension;
        this.position = Objects.requireNonNull(position).toImmutable();
        this.maximumVoltage = maximumVoltage;
        this.parallelLimit = parallelLimit;
    }

    public ResourceLocation machineId() {
        return machineId;
    }

    public int dimension() {
        return dimension;
    }

    public BlockPos position() {
        return position;
    }

    public long maximumVoltage() {
        return maximumVoltage;
    }

    public int parallelLimit() {
        return parallelLimit;
    }
}

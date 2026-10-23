package gregtech.api.recipes.conditions;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;

/** Read-only environment access. Implementations must never load a chunk to answer a query. */
public interface RecipeConditionContext {

    boolean isAvailable();

    BlockPos position();

    int dimension();

    long worldTime();

    boolean isDaytime();

    float rainStrength();

    float thunderStrength();

    @Nullable
    ResourceLocation biome();

    /** Null means unavailable, including an unloaded neighboring chunk. */
    @Nullable
    IBlockState blockAt(BlockPos position);
}

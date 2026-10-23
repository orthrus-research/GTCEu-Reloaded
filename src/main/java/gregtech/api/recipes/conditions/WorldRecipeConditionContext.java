package gregtech.api.recipes.conditions;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/** A short-lived server evaluation view; no World is exposed to condition adapters. */
public final class WorldRecipeConditionContext implements RecipeConditionContext {

    private final World world;
    private final BlockPos position;

    public WorldRecipeConditionContext(@Nullable World world, BlockPos position) {
        this.world = world;
        this.position = position.toImmutable();
    }

    @Override
    public boolean isAvailable() {
        return world != null && world.isBlockLoaded(position, false);
    }

    @Override
    public BlockPos position() {
        return position;
    }

    @Override
    public int dimension() {
        return world.provider.getDimension();
    }

    @Override
    public long worldTime() {
        return world.getWorldTime();
    }

    @Override
    public boolean isDaytime() {
        return world.isDaytime();
    }

    @Override
    public float rainStrength() {
        return world.getRainStrength(1);
    }

    @Override
    public float thunderStrength() {
        return world.getThunderStrength(1);
    }

    @Override
    public @Nullable ResourceLocation biome() {
        return isAvailable() ? world.getBiome(position).getRegistryName() : null;
    }

    @Override
    public @Nullable IBlockState blockAt(BlockPos target) {
        return world != null && world.isBlockLoaded(target, false) ? world.getBlockState(target) : null;
    }
}

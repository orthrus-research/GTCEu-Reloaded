package gregtech.api.recipes.conditions;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TestRecipeConditionContext implements RecipeConditionContext {

    boolean available = true;
    boolean daytime = true;
    long time = 6000;
    float rain;
    float thunder;
    BlockPos pos = new BlockPos(0, 64, 0);
    ResourceLocation biome = new ResourceLocation("minecraft", "plains");
    final Map<BlockPos, IBlockState> blocks = new HashMap<>();
    final Set<BlockPos> unloaded = new HashSet<>();
    final Set<BlockPos> reads = new HashSet<>();

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    @Override
    public int dimension() {
        return 0;
    }

    @Override
    public long worldTime() {
        return time;
    }

    @Override
    public boolean isDaytime() {
        return daytime;
    }

    @Override
    public float rainStrength() {
        return rain;
    }

    @Override
    public float thunderStrength() {
        return thunder;
    }

    @Override
    public ResourceLocation biome() {
        return biome;
    }

    @Override
    public IBlockState blockAt(BlockPos position) {
        reads.add(position.toImmutable());
        return unloaded.contains(position) ? null : blocks.getOrDefault(position, Blocks.AIR.getDefaultState());
    }
}

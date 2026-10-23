package gregtech.api.recipes.conditions;

import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import java.util.HashSet;
import java.util.Set;

final class ConditionTestWorld extends DummyWorld {

    final Set<BlockPos> unloaded = new HashSet<>();
    final Set<BlockPos> checked = new HashSet<>();
    int reads;

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        if (checked != null) checked.add(pos.toImmutable());
        return unloaded == null || !unloaded.contains(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        if (!checked.remove(pos) || unloaded.contains(pos)) throw new AssertionError("Unguarded state read " + pos);
        reads++;
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        if (!checked.remove(pos) || unloaded.contains(pos)) throw new AssertionError("Unguarded biome read " + pos);
        reads++;
        return Biomes.PLAINS;
    }

    @Override
    public Chunk getChunk(int x, int z) {
        throw new AssertionError("Condition forced a chunk lookup");
    }
}

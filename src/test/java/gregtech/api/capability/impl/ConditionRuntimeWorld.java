package gregtech.api.capability.impl;

import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ConditionRuntimeWorld extends DummyWorld {

    boolean daytime = true;
    boolean forbidConditionReads;
    int conditionReads;
    final Set<BlockPos> unloaded = new HashSet<>();
    final Map<BlockPos, IBlockState> states = new HashMap<>();
    final Map<BlockPos, Biome> biomes = new HashMap<>();

    @Override
    public boolean isDaytime() {
        if (forbidConditionReads) throw new AssertionError("Completed work rechecked a processing condition");
        conditionReads++;
        return daytime;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return unloaded == null || !unloaded.contains(pos);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return biomes.getOrDefault(pos, Biomes.PLAINS);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return states.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public Chunk getChunk(int x, int z) {
        throw new AssertionError("Recipe condition forced a chunk lookup");
    }
}

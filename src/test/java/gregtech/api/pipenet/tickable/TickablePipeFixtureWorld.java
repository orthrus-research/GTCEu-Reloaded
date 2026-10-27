package gregtech.api.pipenet.tickable;

import gregtech.api.util.world.DummyWorld;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

final class TickablePipeFixtureWorld extends DummyWorld {

    final Set<ChunkPos> loaded = new HashSet<>();
    long time;

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return this.loaded != null && this.loaded.contains(new ChunkPos(pos));
    }

    @Override
    public long getTotalWorldTime() {
        return this.time;
    }
}

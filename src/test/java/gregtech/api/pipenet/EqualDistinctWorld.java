package gregtech.api.pipenet;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

final class EqualDistinctWorld extends WorldPipeNet<Integer, EqualDistinctNetwork> {

    EqualDistinctWorld() {
        super("equal_distinct_network_test");
    }

    @Override
    protected EqualDistinctNetwork createNetInstance() {
        return new EqualDistinctNetwork(this);
    }

    int networkCount() {
        return this.pipeNets.size();
    }

    int chunkOwners(BlockPos pos) {
        return this.pipeNetsByChunk.get(new ChunkPos(pos)).size();
    }

    void republish(EqualDistinctNetwork net) {
        addPipeNetSilently(net);
    }
}

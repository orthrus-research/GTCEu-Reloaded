package gregtech.api.pipenet;

import net.minecraft.util.math.ChunkPos;

final class PipeNetTestWorld extends WorldPipeNet<Integer, PipeNetTestNetwork> {

    PipeNetTestWorld() {
        super("pipe_net_correctness_test");
    }

    @Override
    protected PipeNetTestNetwork createNetInstance() {
        return new PipeNetTestNetwork(this);
    }

    int networkCount() {
        return this.pipeNets.size();
    }

    int indexedChunkCount() {
        return this.pipeNetsByChunk.size();
    }

    int networksInChunk(ChunkPos chunk) {
        return this.pipeNetsByChunk.getOrDefault(chunk, java.util.Collections.emptyList()).size();
    }

    java.util.List<PipeNetTestNetwork> networks() {
        return new java.util.ArrayList<>(this.pipeNets);
    }
}

package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

final class PipeNetPhaseNetwork extends PipeNet<Integer> {

    private BlockPos anchor;
    private BlockPos remote;
    private boolean expectedSameOwner;
    int observedCallbacks;
    boolean callbacksAfterPublication = true;

    PipeNetPhaseNetwork(PipeNetPhaseWorld world) {
        super(world);
    }

    void observe(BlockPos anchor, BlockPos remote, boolean expectedSameOwner) {
        this.anchor = anchor;
        this.remote = remote;
        this.expectedSameOwner = expectedSameOwner;
    }

    @Override
    protected void onNodeConnectionsUpdate() {
        super.onNodeConnectionsUpdate();
        if (this.anchor != null) {
            this.observedCallbacks++;
            this.callbacksAfterPublication &= this.worldData.getNetFromPos(this.anchor) == this &&
                    (this.worldData.getNetFromPos(this.remote) == this) == this.expectedSameOwner;
        }
    }

    @Override
    protected void writeNodeData(Integer data, NBTTagCompound tag) {
        tag.setInteger("value", data);
    }

    @Override
    protected Integer readNodeData(NBTTagCompound tag) {
        return tag.getInteger("value");
    }
}

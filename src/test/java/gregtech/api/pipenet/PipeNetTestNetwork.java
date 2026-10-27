package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants.NBT;

final class PipeNetTestNetwork extends PipeNet<Integer> {

    int routeInvalidations;
    boolean requestNestedInvalidation;

    PipeNetTestNetwork(PipeNetTestWorld world) {
        super(world);
    }

    @Override
    protected void writeNodeData(Integer data, NBTTagCompound tag) {
        tag.setInteger("value", data);
    }

    @Override
    protected Integer readNodeData(NBTTagCompound tag) {
        if (!tag.hasKey("value", NBT.TAG_INT)) {
            throw new IllegalArgumentException("Missing test property value");
        }
        return tag.getInteger("value");
    }

    @Override
    public void onPipeConnectionsUpdate() {
        this.routeInvalidations++;
        if (this.requestNestedInvalidation) {
            worldData.invalidateRoutes();
        }
    }

    void addSilently(BlockPos pos, Node<Integer> node) {
        addNodeSilently(pos, node);
    }

    void removeSilently(BlockPos pos) {
        removeNodeWithoutRebuilding(pos);
    }
}

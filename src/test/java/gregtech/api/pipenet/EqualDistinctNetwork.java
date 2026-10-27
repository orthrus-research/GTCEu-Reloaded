package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;

final class EqualDistinctNetwork extends PipeNet<Integer> {

    EqualDistinctNetwork(EqualDistinctWorld world) {
        super(world);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EqualDistinctNetwork;
    }

    @Override
    public int hashCode() {
        return 1;
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

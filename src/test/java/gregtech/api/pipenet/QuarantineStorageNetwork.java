package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;

final class QuarantineStorageNetwork extends PipeNet<Integer> {

    QuarantineStorageNetwork(QuarantineStorageWorld world) {
        super(world);
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

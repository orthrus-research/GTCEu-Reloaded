package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;

final class WorldAwareCallbackNetwork extends PipeNet<Integer> {

    int callbacks;
    long observedTime;

    WorldAwareCallbackNetwork(WorldAwareCallbackWorld world) {
        super(world);
    }

    @Override
    public void onPipeConnectionsUpdate() {
        this.observedTime = getWorldData().getTotalWorldTime();
        this.callbacks++;
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

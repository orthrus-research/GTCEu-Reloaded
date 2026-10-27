package gregtech.api.pipenet.tickable;

import gregtech.api.pipenet.PipeNet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;

final class TickablePipeFixtureNetwork extends PipeNet<Integer> implements ITickable {

    int updates;
    Runnable action;

    TickablePipeFixtureNetwork(TickablePipeFixtureGraph graph) {
        super(graph);
    }

    @Override
    public void update() {
        this.updates++;
        if (this.action != null) this.action.run();
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

package gregtech.api.pipenet.tickable;

import gregtech.api.pipenet.PipeNet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;

final class TickablePipeFixtureNetwork extends PipeNet<Integer> implements ITickable {

    int updates;
    Runnable action;
    private final boolean valueEqual;

    TickablePipeFixtureNetwork(TickablePipeFixtureGraph graph) {
        super(graph);
        this.valueEqual = graph.valueEqualNetworks;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || this.valueEqual && other instanceof TickablePipeFixtureNetwork network &&
                network.valueEqual;
    }

    @Override
    public int hashCode() {
        return this.valueEqual ? 1 : System.identityHashCode(this);
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

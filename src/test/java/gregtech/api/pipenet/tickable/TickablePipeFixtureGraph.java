package gregtech.api.pipenet.tickable;

import net.minecraft.world.World;

final class TickablePipeFixtureGraph extends TickableWorldPipeNet<Integer, TickablePipeFixtureNetwork> {

    int rate = 1;
    boolean valueEqualNetworks;

    TickablePipeFixtureGraph() {
        super("tickable_pipe_correctness_test");
    }

    void attach(World world) {
        setWorldAndInit(world);
    }

    @Override
    protected int getUpdateRate() {
        return this.rate;
    }

    @Override
    protected TickablePipeFixtureNetwork createNetInstance() {
        return new TickablePipeFixtureNetwork(this);
    }
}

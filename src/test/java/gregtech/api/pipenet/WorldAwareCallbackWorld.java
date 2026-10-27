package gregtech.api.pipenet;

import net.minecraft.world.World;

final class WorldAwareCallbackWorld extends WorldPipeNet<Integer, WorldAwareCallbackNetwork> {

    WorldAwareCallbackWorld() {
        super("world_aware_callback_test");
    }

    void attach(World world) {
        setWorldAndInit(world);
    }

    @Override
    protected WorldAwareCallbackNetwork createNetInstance() {
        return new WorldAwareCallbackNetwork(this);
    }
}

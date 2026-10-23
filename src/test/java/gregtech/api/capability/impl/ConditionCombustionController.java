package gregtech.api.capability.impl;

import net.minecraft.world.World;

final class ConditionCombustionController extends PendingCombustionTestController {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public boolean isStructureObstructed() {
        return false;
    }
}

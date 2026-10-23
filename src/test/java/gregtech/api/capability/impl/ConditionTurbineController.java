package gregtech.api.capability.impl;

import net.minecraft.world.World;

final class ConditionTurbineController extends PendingTurbineTestController {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();
    int maintenanceCalls;

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public boolean isStructureObstructed() {
        return false;
    }

    @Override
    public void calculateMaintenance() {
        maintenanceCalls++;
    }

    @Override
    public void checkStructurePattern() {}
}

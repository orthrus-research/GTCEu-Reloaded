package gregtech.api.capability.impl;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.recipes.RecipeMap;

import net.minecraft.world.World;

import java.util.Collection;

final class ConditionComputationController extends PendingComputationTestController {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();
    boolean computationAvailable = true;

    ConditionComputationController(RecipeMap<?> map) {
        super(map);
    }

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        int result = super.requestCWUt(cwut, simulate, seen);
        return computationAvailable ? result : 0;
    }
}

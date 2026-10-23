package gregtech.api.capability.impl;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class ConditionRuntimeMachine extends PendingOutputTestMachine {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();
    BlockPos position = new BlockPos(32, 64, 48);

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public BlockPos getPos() {
        return position;
    }

    @Override
    public boolean supportsIdleRecipeScheduling() {
        return true;
    }
}

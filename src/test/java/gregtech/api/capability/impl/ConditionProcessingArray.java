package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collections;
import java.util.List;

final class ConditionProcessingArray extends MetaTileEntityProcessingArray {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();
    final ItemStackHandler machineSlot = new ItemStackHandler(1);
    final BlockPos position = new BlockPos(96, 180, -80);

    ConditionProcessingArray() {
        super(GTUtility.gregtechId("condition_processing_array"), 0);
    }

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public BlockPos getPos() {
        return position;
    }

    @Override
    public void markDirty() {}

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        return ability == MultiblockAbility.MACHINE_HATCH ? (List<T>) Collections.singletonList(machineSlot) :
                super.getAbilities(ability);
    }

    void discoverInstalledMachine() {
        ((ProcessingArrayWorkable) getRecipeMapWorkable()).findMachineStack();
    }
}

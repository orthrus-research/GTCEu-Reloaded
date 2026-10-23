package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityRotorHolder;

import net.minecraft.world.World;

class PendingRotorTestPart extends MetaTileEntityRotorHolder {

    private final MetaTileEntityLargeTurbine controller;
    int wearCalls;

    PendingRotorTestPart(MetaTileEntityLargeTurbine controller) {
        super(GTUtility.gregtechId("pending_rotor_test"), GTValues.HV);
        this.controller = controller;
        IdleRecipeTestMachine.attachHolder(this);
    }

    @Override
    public MetaTileEntityLargeTurbine getController() {
        return controller;
    }

    @Override
    public World getWorld() {
        return DummyWorld.INSTANCE;
    }

    @Override
    public long getOffsetTimer() {
        return 20;
    }

    @Override
    public boolean hasRotor() {
        return true;
    }

    @Override
    public void damageRotor(int amount) {
        wearCalls++;
    }

    @Override
    public void markDirty() {}
}

package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeCombustionEngine;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

class PendingCombustionTestController extends MetaTileEntityLargeCombustionEngine {

    long tick = 1;
    final FluidTank lubricant = new FluidTank(Materials.Lubricant.getFluid(100), 1000);
    final FluidTank oxygen = new FluidTank(Materials.Oxygen.getFluid(100), 1000);

    PendingCombustionTestController() {
        super(GTUtility.gregtechId("pending_combustion_test"), GTValues.EV);
        this.energyContainer = new EnergyContainerHandler(this, 10000, 0, 0, GTValues.V[GTValues.EV], 1);
        this.outputInventory = new ItemStackHandler(1);
        this.inputFluidInventory = new FluidTankList(false, lubricant, oxygen);
        this.extendedFluidInputs = inputFluidInventory;
        IdleRecipeTestMachine.attachHolder(this);
    }

    @Override
    public World getWorld() {
        return DummyWorld.INSTANCE;
    }

    @Override
    public long getOffsetTimer() {
        return tick;
    }

    @Override
    public boolean isStructureFormed() {
        return true;
    }

    @Override
    public void markDirty() {}
}

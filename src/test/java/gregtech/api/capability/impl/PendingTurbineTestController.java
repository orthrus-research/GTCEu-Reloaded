package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

class PendingTurbineTestController extends MetaTileEntityLargeTurbine {

    long tick = 1;
    boolean formed = true;
    boolean voidFluids;
    final FluidTank products = new FluidTank(new FluidStack(FluidRegistry.LAVA, 1000), 1000);

    PendingTurbineTestController() {
        super(GTUtility.gregtechId("pending_turbine_test"), RecipeFailureTest.map(), GTValues.HV,
                null, null, null, false, null);
        this.energyContainer = new EnergyContainerHandler(this, 10000, 0, 0, GTValues.V[GTValues.HV], 1);
        this.outputFluidInventory = new FluidTankList(true, products);
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
        return formed;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        // Ordinary turbines always allow voiding. Exercise pending behavior for an addon that opts out.
        return voidFluids;
    }

    @Override
    public void markDirty() {}
}

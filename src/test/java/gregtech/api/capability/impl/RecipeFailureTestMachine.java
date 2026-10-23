package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

class RecipeFailureTestMachine extends MetaTileEntity implements ICleanroomReceiver {

    long tick;
    ICleanroomProvider cleanroom;

    RecipeFailureTestMachine() {
        super(GTUtility.gregtechId("recipe_failure_test"));
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeFailureTestMachine();
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 2, this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, 2, this, true);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return new FluidTankList(false, new FluidTank(1000));
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
    public boolean isValid() {
        return true;
    }

    @Override
    public void markDirty() {}

    @Override
    public ICleanroomProvider getCleanroom() {
        return cleanroom;
    }

    @Override
    public void setCleanroom(ICleanroomProvider provider) {
        this.cleanroom = provider;
    }

    @Override
    public void unsetCleanroom() {
        this.cleanroom = null;
    }
}

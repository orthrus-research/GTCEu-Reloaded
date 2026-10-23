package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandlerModifiable;

final class RecipeCandidateTestMachine extends MetaTileEntity {

    long tick;

    RecipeCandidateTestMachine() {
        super(GTUtility.gregtechId("recipe_candidate_test"));
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeCandidateTestMachine();
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
}

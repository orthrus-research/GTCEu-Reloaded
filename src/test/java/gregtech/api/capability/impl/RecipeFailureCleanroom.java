package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;

final class RecipeFailureCleanroom implements ICleanroomProvider {

    boolean clean;
    boolean correctType;
    int cleanChecks;
    int typeChecks;

    @Override
    public boolean isClean() {
        cleanChecks++;
        return clean;
    }

    @Override
    public boolean checkCleanroomType(CleanroomType type) {
        typeChecks++;
        return correctType;
    }

    @Override
    public void setCleanAmount(int amount) {
        throw new AssertionError("Diagnostics must not change the cleanroom");
    }

    @Override
    public void adjustCleanAmount(int amount) {
        throw new AssertionError("Diagnostics must not change the cleanroom");
    }

    @Override
    public boolean drainEnergy(boolean simulate) {
        throw new AssertionError("Diagnostics must not probe cleanroom energy");
    }

    @Override
    public long getEnergyInputPerSecond() {
        throw new AssertionError("Diagnostics must not query cleanroom throughput");
    }

    @Override
    public int getEnergyTier() {
        throw new AssertionError("Diagnostics must not query cleanroom tier");
    }
}

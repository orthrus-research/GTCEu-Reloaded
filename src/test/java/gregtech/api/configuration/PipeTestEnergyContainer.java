package gregtech.api.configuration;

import gregtech.api.capability.ILaserContainer;

import net.minecraft.util.EnumFacing;

final class PipeTestEnergyContainer implements ILaserContainer {

    long stored;
    long capacity = 100000;
    long returnedAmps = -1;
    long lastVoltage;
    int calls;
    boolean throwNext;

    @Override
    public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
        this.calls++;
        this.lastVoltage = voltage;
        if (this.throwNext) {
            this.throwNext = false;
            throw new IllegalStateException("Expected destination failure");
        }
        long accepted = this.returnedAmps == -1 ? amperage : this.returnedAmps;
        this.stored += Math.max(0, accepted) * voltage;
        return accepted;
    }

    @Override
    public boolean inputsEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public long changeEnergy(long amount) {
        this.stored += amount;
        return amount;
    }

    @Override
    public long getEnergyStored() {
        return this.stored;
    }

    @Override
    public long getEnergyCapacity() {
        return this.capacity;
    }

    @Override
    public long getInputAmperage() {
        return 16;
    }

    @Override
    public long getInputVoltage() {
        return 128;
    }
}

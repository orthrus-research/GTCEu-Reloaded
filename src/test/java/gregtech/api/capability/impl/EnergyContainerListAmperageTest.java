package gregtech.api.capability.impl;

import gregtech.api.capability.IEnergyContainer;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnergyContainerListAmperageTest {

    /** Accepts up to {@code maxAmps} per call and records what it took. */
    private static final class Sink implements IEnergyContainer {

        private final long maxAmps;
        long taken;

        Sink(long maxAmps) {
            this.maxAmps = maxAmps;
        }

        @Override
        public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
            long accepted = Math.min(amperage, maxAmps);
            taken += accepted;
            return accepted;
        }

        @Override
        public boolean inputsEnergy(EnumFacing side) {
            return true;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            return 0;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            return Long.MAX_VALUE;
        }

        @Override
        public long getInputAmperage() {
            return maxAmps;
        }

        @Override
        public long getInputVoltage() {
            return 32;
        }
    }

    @Test
    public void acceptsNoMoreAmperesThanOffered() {
        Sink first = new Sink(2);
        Sink second = new Sink(2);
        EnergyContainerList list = new EnergyContainerList(Arrays.asList(first, second));

        assertEquals(3, list.acceptEnergyFromNetwork(null, 32, 3));
        assertEquals(2, first.taken);
        assertEquals(1, second.taken);
    }
}

package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.multiblock.IBatteryData;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PowerSubstationDrainTest {

    private static IBatteryData battery(long capacity) {
        return new IBatteryData() {

            @Override
            public int getTier() {
                return 1;
            }

            @Override
            public long getCapacity() {
                return capacity;
            }

            @Override
            public @NotNull String getBatteryName() {
                return "test";
            }
        };
    }

    @Test
    public void rebuildDoesNotCarryTheOldRemainder() {
        // one EU short of a full EU/t of drain; a carried remainder would tip the rebuilt bank over
        var bank = new MetaTileEntityPowerSubstation.PowerStationEnergyBank(
                Collections.singletonList(battery(PASSIVE_DRAIN_DIVISOR - 1)));
        List<IBatteryData> small = Collections.singletonList(battery(2));
        bank.rebuild(small);
        assertEquals(new MetaTileEntityPowerSubstation.PowerStationEnergyBank(small).getPassiveDrainPerTick(),
                bank.getPassiveDrainPerTick());
    }
}

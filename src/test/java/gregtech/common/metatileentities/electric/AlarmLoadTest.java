package gregtech.common.metatileentities.electric;

import gregtech.BootstrapExtension;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BootstrapExtension.class)
class AlarmLoadTest {

    @Test
    void anAlarmSavedWithoutARadiusLoadsTheDefaultAndASavedRadiusStaysInRange() {
        MetaTileEntityAlarm alarm = new MetaTileEntityAlarm(gregtechId("load_alarm"));
        alarm.readFromNBT(new NBTTagCompound());
        assertEquals(64 / 16f, alarm.getVolume());

        NBTTagCompound tooFar = new NBTTagCompound();
        tooFar.setInteger("radius", 10_000);
        alarm.readFromNBT(tooFar);
        assertEquals(128 / 16f, alarm.getVolume());

        NBTTagCompound negative = new NBTTagCompound();
        negative.setInteger("radius", -5);
        alarm.readFromNBT(negative);
        assertEquals(0f, alarm.getVolume());
    }
}

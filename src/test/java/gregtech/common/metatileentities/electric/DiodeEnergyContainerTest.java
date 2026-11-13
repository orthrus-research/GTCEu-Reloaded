package gregtech.common.metatileentities.electric;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

public class DiodeEnergyContainerTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void newDiodeHasCapacityForItsMaximumAmperage() {
        var diode = new MetaTileEntityDiode(GTUtility.gregtechId("test_diode"), GTValues.LV, 16);
        MatcherAssert.assertThat(diode.energyContainer.getEnergyCapacity(), is(GTValues.V[GTValues.LV] * 16));
        MatcherAssert.assertThat(diode.energyContainer.getInputAmperage(), is(1L));
    }

    @Test
    public void placedCopyHasTheSameCapacity() {
        var diode = new MetaTileEntityDiode(GTUtility.gregtechId("test_diode_copy"), GTValues.MV, 4);
        var copy = (MetaTileEntityDiode) diode.createMetaTileEntity(null);
        MatcherAssert.assertThat(copy.energyContainer.getEnergyCapacity(), is(GTValues.V[GTValues.MV] * 4));
    }
}

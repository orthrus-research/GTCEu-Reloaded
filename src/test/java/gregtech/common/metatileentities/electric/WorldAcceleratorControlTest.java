package gregtech.common.metatileentities.electric;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

public class WorldAcceleratorControlTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void setWorkingEnabledControlsTheMachine() {
        var accelerator = new MetaTileEntityWorldAccelerator(GTUtility.gregtechId("test_world_accelerator"),
                GTValues.LV);
        MatcherAssert.assertThat(accelerator.isWorkingEnabled(), is(true));

        accelerator.setWorkingEnabled(false);
        MatcherAssert.assertThat(accelerator.isWorkingEnabled(), is(false));

        accelerator.setWorkingEnabled(true);
        MatcherAssert.assertThat(accelerator.isWorkingEnabled(), is(true));
    }

    @Test
    public void togglingLikeTheSoftMalletChangesTheState() {
        var accelerator = new MetaTileEntityWorldAccelerator(GTUtility.gregtechId("test_world_accelerator_toggle"),
                GTValues.LV);

        accelerator.setWorkingEnabled(!accelerator.isWorkingEnabled());
        MatcherAssert.assertThat(accelerator.isWorkingEnabled(), is(false));

        accelerator.setWorkingEnabled(!accelerator.isWorkingEnabled());
        MatcherAssert.assertThat(accelerator.isWorkingEnabled(), is(true));
    }
}

package gregtech.api.fluids;

import gregtech.Bootstrap;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;

import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.fluids.FluidConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidTemperaturePreviewTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void explicitTemperatureAndStateTakePrecedence() {
        var explicit = new FluidBuilder().temperature(713);
        assertEquals(713, explicit.previewTemperature(Materials.Steel, FluidStorageKeys.PLASMA));
        assertEquals(713, explicit.previewTemperature(null, null));
        var gas = new FluidBuilder().state(FluidState.GAS);
        assertEquals(ROOM_TEMPERATURE, gas.previewTemperature(Materials.Copper, FluidStorageKeys.LIQUID));
    }

    @Test
    void previewsDoNotPinAnInferredTemperatureOrState() {
        var builder = new FluidBuilder().name("temperature_preview_does_not_register");
        assertNull(FluidRegistry.getFluid("temperature_preview_does_not_register"));
        assertEquals(SOLID_LIQUID_TEMPERATURE, builder.previewTemperature(Materials.Copper, FluidStorageKeys.LIQUID));
        assertEquals(ROOM_TEMPERATURE, builder.previewTemperature(Materials.Copper, FluidStorageKeys.GAS));
        assertEquals(SOLID_LIQUID_TEMPERATURE, builder.previewTemperature(Materials.Copper, null));
        assertEquals(ROOM_TEMPERATURE, builder.previewTemperature(null, FluidStorageKeys.PLASMA));
        assertNull(FluidRegistry.getFluid("temperature_preview_does_not_register"));
    }

    @Test
    void blastAndRegisteredPlasmaDefaultsMatchNativeRules() {
        var builder = new FluidBuilder();
        int blast = Materials.Steel.getProperty(PropertyKey.BLAST).getBlastTemperature();
        assertEquals(blast + LIQUID_TEMPERATURE_OFFSET,
                builder.previewTemperature(Materials.Steel, FluidStorageKeys.LIQUID));
        assertEquals(blast + GAS_TEMPERATURE_OFFSET, builder.previewTemperature(Materials.Steel, FluidStorageKeys.GAS));
        assertEquals(blast + BASE_PLASMA_TEMPERATURE,
                builder.previewTemperature(Materials.Steel, FluidStorageKeys.PLASMA));
        assertEquals(BASE_PLASMA_TEMPERATURE + Materials.Copper.getFluid().getTemperature(),
                builder.previewTemperature(Materials.Copper, FluidStorageKeys.PLASMA));
        assertEquals(BASE_PLASMA_TEMPERATURE, builder.previewTemperature(Materials.Iodine, FluidStorageKeys.PLASMA));
    }

    @Test
    void actualFluidBuildUsesThePreviewWithoutEarlierQueriesChangingIt() {
        int index = 0;
        for (var material : new gregtech.api.unification.material.Material[] { Materials.Copper, Materials.Steel }) {
            for (var key : java.util.List.of(FluidStorageKeys.LIQUID, FluidStorageKeys.GAS, FluidStorageKeys.PLASMA)) {
                var builder = new FluidBuilder().name("temperature_preview_build_" + index++).disableBucket();
                builder.previewTemperature(material, FluidStorageKeys.GAS);
                int expected = builder.previewTemperature(material, key);
                assertEquals(expected, builder.build("gregtech", material, key).getTemperature());
            }
        }
    }
}

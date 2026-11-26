package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.GTValues;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SteamAccountingTest {

    private final List<String> drains = new ArrayList<>();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private SteamAccounting.Drain recording(boolean gives) {
        return (amount, doDrain) -> {
            drains.add(amount + (doDrain ? " drained" : " simulated"));
            return gives ? new FluidStack(FluidRegistry.WATER, amount) : null;
        };
    }

    @Test
    void steamLogicsRunRecipesUpToLV() {
        assertEquals(GTValues.V[GTValues.LV], SteamAccounting.MAX_VOLTAGE);
    }

    @Test
    void storedSteamRoundsUpAndCapacityRoundsDown() {
        SteamAccounting half = new SteamAccounting(0.5);
        assertEquals(3, half.getEnergyStored(5));
        assertEquals(2, half.getEnergyCapacity(5));
        SteamAccounting one = new SteamAccounting(1);
        assertEquals(16000, one.getEnergyStored(16000));
        assertEquals(16000, one.getEnergyCapacity(16000));
    }

    @Test
    void energyIsDrawnAsWholeMillibucketsRoundedUp() {
        SteamAccounting half = new SteamAccounting(0.5);
        assertTrue(half.drawEnergy(3, true, 6, recording(true)));
        assertTrue(half.drawEnergy(3, false, 6, recording(true)));
        assertEquals(List.of("6 simulated", "6 drained"), drains);

        SteamAccounting third = new SteamAccounting(3);
        drains.clear();
        assertTrue(third.drawEnergy(4, false, 2, recording(true)));
        assertEquals(List.of("2 drained"), drains, "4 EU at 3 EU per millibucket draws 2 millibuckets");
    }

    @Test
    void tooLittleSteamIsNotDrainedAtAll() {
        SteamAccounting half = new SteamAccounting(0.5);
        assertFalse(half.drawEnergy(3, false, 5, recording(true)));
        assertFalse(half.drawEnergy(-3, false, 100, recording(true)), "Steam is never added back by drawing");
        assertTrue(drains.isEmpty());
    }

    @Test
    void tanksThatGiveNothingFailTheDraw() {
        SteamAccounting one = new SteamAccounting(1);
        assertFalse(one.drawEnergy(8, false, 100, recording(false)));
        assertEquals(List.of("8 drained"), drains);
    }

    @Test
    void onlyARecipeWithPositiveSteamConsumesIt() {
        SteamAccounting one = new SteamAccounting(1);
        assertTrue(one.consumesSteam(16, 10));
        assertFalse(one.consumesSteam(0, 10));
        assertFalse(one.consumesSteam(-16, 10));
        assertFalse(new SteamAccounting(4).consumesSteam(1, 3), "Under one millibucket for the whole run");
    }

    @Test
    void aRecipeNeedsTheSteamForItsWholeRunOrAFullBuffer() {
        SteamAccounting one = new SteamAccounting(1);
        assertFalse(one.hasSteamForRecipe(16, 10, 159, 1000));
        assertTrue(one.hasSteamForRecipe(16, 10, 160, 1000));
        assertTrue(one.hasSteamForRecipe(16, 10, 100, 100), "A run larger than the buffer needs the full buffer");
        assertFalse(one.hasSteamForRecipe(16, 10, 99, 100));
    }

    @Test
    void theRunsSteamIsComparedWithTheStoredEnergyAsBefore() {
        SteamAccounting half = new SteamAccounting(0.5);
        // 1 EU/t for 100 ticks is 200 millibuckets; the stored energy, at 0.5 EU each, must reach 200 as well
        assertFalse(half.hasSteamForRecipe(1, 100, half.getEnergyStored(200), half.getEnergyCapacity(16000)));
        assertTrue(half.hasSteamForRecipe(1, 100, half.getEnergyStored(400), half.getEnergyCapacity(16000)));
    }
}

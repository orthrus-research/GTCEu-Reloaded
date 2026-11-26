package gregtech.integration.probe.producer;

import gregtech.api.GTValues;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuantumStorageProbeInfoTest {

    private static final String USAGE = "&c32<info> EU/t&a (" + GTValues.VNF[GTValues.LV].replace('§', '&') + "&a)";

    private static List<String> controller(boolean hasEnergyHatches, boolean powered) {
        List<ProbeLine> lines = new ArrayList<>();
        QuantumStorageProbeInfo.addControllerLines(32, hasEnergyHatches, powered, lines);
        return PlainProbeRenderer.render(lines);
    }

    private static List<String> storage(boolean connected, boolean powered) {
        List<ProbeLine> lines = new ArrayList<>();
        QuantumStorageProbeInfo.addStorageLines(connected, powered, lines);
        return PlainProbeRenderer.render(lines);
    }

    @Test
    public void aPoweredControllerShowsItsUse() {
        assertEquals(List.of("<info>[gregtech.top.energy_consumption] " + USAGE), controller(true, true));
    }

    @Test
    public void aControllerSaysWhatItLacks() {
        assertEquals(List.of("[gregtech.top.quantum_controller.no_hatches]",
                "<info>[gregtech.top.energy_required] " + USAGE), controller(false, false));
        assertEquals(List.of("[gregtech.top.quantum_controller.no_power]",
                "<info>[gregtech.top.energy_required] " + USAGE), controller(true, false));
    }

    @Test
    public void storageShowsItsConnection() {
        assertEquals(List.of("<info>[gregtech.top.quantum_status.label] [gregtech.top.quantum_status.powered]"),
                storage(true, true));
        assertEquals(List.of("<info>[gregtech.top.quantum_status.label] [gregtech.top.quantum_status.connected]"),
                storage(true, false));
        assertEquals(List.of("<info>[gregtech.top.quantum_status.label] [gregtech.top.quantum_status.disconnected]"),
                storage(false, false));
    }
}

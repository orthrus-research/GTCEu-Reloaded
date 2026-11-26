package gregtech.integration.probe.producer;

import gregtech.api.GTValues;
import gregtech.api.capability.IQuantumController;
import gregtech.api.capability.IQuantumStorage;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * A quantum controller's energy use and what keeps it from working; for a quantum chest or tank, whether it is
 * connected to a powered controller.
 */
public class QuantumStorageProbeInfo implements ProbeInfoProducer {

    @Override
    public void addInfo(ProbeContext context, List<ProbeLine> lines) {
        MetaTileEntity metaTileEntity = context.metaTileEntity();
        if (metaTileEntity instanceof IQuantumController controller) {
            addControllerLines(controller.getEnergyUsage() / 10, controller.getCount(IQuantumStorage.Type.ENERGY) > 0,
                    controller.isPowered(), lines);
        } else if (metaTileEntity instanceof IQuantumStorage<?> storage && holdsItemsOrFluids(storage)) {
            IQuantumController controller = storage.getQuantumController();
            addStorageLines(controller != null, controller != null && controller.isPowered(), lines);
        }
    }

    private static boolean holdsItemsOrFluids(IQuantumStorage<?> storage) {
        return storage.getType() == IQuantumStorage.Type.ITEM || storage.getType() == IQuantumStorage.Type.FLUID;
    }

    static void addControllerLines(long energyUsage, boolean hasEnergyHatches, boolean powered,
                                   List<ProbeLine> lines) {
        ProbeText energy = ProbeText.builder()
                .format(TextFormatting.RED)
                .literal(Long.toString(energyUsage))
                .style(ProbeStyle.INFO)
                .literal(" EU/t")
                .format(TextFormatting.GREEN)
                .literal(" (" + GTValues.VNF[GTUtility.getTierByVoltage(energyUsage)])
                .format(TextFormatting.GREEN)
                .literal(")")
                .build();
        String usageKey = "gregtech.top.energy_required";
        if (!hasEnergyHatches) {
            lines.add(ProbeLine.text(ProbeText.translation("gregtech.top.quantum_controller.no_hatches")));
        } else if (!powered) {
            lines.add(ProbeLine.text(ProbeText.translation("gregtech.top.quantum_controller.no_power")));
        } else {
            usageKey = "gregtech.top.energy_consumption";
        }
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation(usageKey)
                .literal(" ")
                .append(energy)
                .build()));
    }

    static void addStorageLines(boolean connected, boolean powered, List<ProbeLine> lines) {
        String status = "gregtech.top.quantum_status.disconnected";
        if (connected) {
            status = powered ? "gregtech.top.quantum_status.powered" : "gregtech.top.quantum_status.connected";
        }
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation("gregtech.top.quantum_status.label")
                .literal(" ")
                .translation(status)
                .build()));
    }
}

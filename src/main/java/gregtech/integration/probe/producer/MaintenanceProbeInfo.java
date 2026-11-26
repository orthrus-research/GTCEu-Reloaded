package gregtech.integration.probe.producer;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.metatileentity.multiblock.IMaintenance;
import gregtech.api.unification.material.Materials;
import gregtech.common.ConfigHolder;
import gregtech.common.items.ToolItems;
import gregtech.integration.probe.CapabilityProbeInfoProducer;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Whether a formed multiblock needs maintenance; while the player sneaks, each problem with the tool that fixes it.
 */
public class MaintenanceProbeInfo extends CapabilityProbeInfoProducer<IMaintenance> {

    /** Each problem's description, by the bit that is clear while the problem is present. */
    private static final String[] PROBLEM_KEYS = {
            "gregtech.top.maintenance.wrench",
            "gregtech.top.maintenance.screwdriver",
            "gregtech.top.maintenance.soft_mallet",
            "gregtech.top.maintenance.hard_hammer",
            "gregtech.top.maintenance.wire_cutter",
            "gregtech.top.maintenance.crowbar"
    };

    @Override
    protected Capability<IMaintenance> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_MAINTENANCE;
    }

    @Override
    protected void addInfo(IMaintenance capability, ProbeContext context, List<ProbeLine> lines) {
        if (!ConfigHolder.machines.enableMaintenance || !capability.hasMaintenanceMechanics()) return;
        IMultiblockController controller = Objects.requireNonNull(context.tileEntity())
                .getCapability(GregtechCapabilities.CAPABILITY_MULTIBLOCK_CONTROLLER, null);
        if (controller == null || !controller.isStructureFormed()) return;

        addLines(capability.hasMaintenanceProblems(), capability.getMaintenanceProblems(), context.sneaking(),
                problem -> ToolIcons.ICONS[problem], lines);
    }

    static void addLines(boolean hasProblems, int problems, boolean sneaking, IntFunction<ItemStack> toolIcons,
                         List<ProbeLine> lines) {
        if (!hasProblems) {
            lines.add(ProbeLine.text(ProbeText.builder()
                    .style(ProbeStyle.OK)
                    .translation("gregtech.top.maintenance_fixed")
                    .build()));
            return;
        }

        if (!sneaking) {
            lines.add(ProbeLine.text(red("gregtech.top.maintenance_broken")));
            return;
        }

        for (int problem = 0; problem < PROBLEM_KEYS.length; problem++) {
            if (((problems >> problem) & 1) == 0) {
                lines.add(ProbeLine.item(toolIcons.apply(problem), red(PROBLEM_KEYS[problem])));
            }
        }
    }

    private static ProbeText red(String key) {
        return ProbeText.builder().format(TextFormatting.RED).translation(key).build();
    }

    /** The tools that fix each problem, made once the tools are registered. */
    private static final class ToolIcons {

        private static final ItemStack[] ICONS = {
                ToolItems.WRENCH.get(Materials.Neutronium),
                ToolItems.SCREWDRIVER.get(Materials.Neutronium),
                ToolItems.SOFT_MALLET.get(Materials.Neutronium),
                ToolItems.HARD_HAMMER.get(Materials.Neutronium),
                ToolItems.WIRE_CUTTER.get(Materials.Neutronium),
                ToolItems.CROWBAR.get(Materials.Neutronium)
        };
    }
}

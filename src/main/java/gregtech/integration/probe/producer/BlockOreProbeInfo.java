package gregtech.integration.probe.producer;

import gregtech.api.unification.ore.StoneType;
import gregtech.common.blocks.BlockOre;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeStyle;
import gregtech.integration.probe.ProbeText;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;

import java.util.List;

/** While the player sneaks, what an ore in a stone that is not dropped itself drops. */
public class BlockOreProbeInfo implements ProbeInfoProducer {

    @Override
    public void addInfo(ProbeContext context, List<ProbeLine> lines) {
        IBlockState state = context.state();
        if (!context.sneaking() || !(state.getBlock() instanceof BlockOre ore)) return;
        StoneType stoneType = state.getValue(ore.STONE_TYPE);
        if (!stoneType.shouldBeDroppedAsItem) addLines(ore.getItem(context.world(), context.pos(), state), lines);
    }

    static void addLines(ItemStack drop, List<ProbeLine> lines) {
        lines.add(ProbeLine.text(ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation("gregtech.top.block_drops")
                .literal(":")
                .build()));
        lines.add(ProbeLine.namedItem(drop));
    }
}

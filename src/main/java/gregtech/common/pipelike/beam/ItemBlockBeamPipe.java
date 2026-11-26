package gregtech.common.pipelike.beam;

import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.block.ItemBlockPipe;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The item of a beam pipe, laser or optical; its tooltip describes the family and how a wire cutter connects it.
 */
public abstract class ItemBlockBeamPipe<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>,
        NodeDataType> extends ItemBlockPipe<PipeType, NodeDataType> {

    private final String tooltipKey;

    protected ItemBlockBeamPipe(BlockPipe<PipeType, NodeDataType, ?> block, String tooltipKey) {
        super(block);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void addInformation(@NotNull ItemStack stack, @Nullable World worldIn, @NotNull List<String> tooltip,
                               @NotNull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format(tooltipKey));

        if (TooltipHelper.isShiftDown()) {
            tooltip.add(I18n.format("gregtech.tool_action.wire_cutter.connect"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.show_tooltips"));
        }
    }
}

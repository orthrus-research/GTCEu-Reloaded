package gregtech.common.pipelike.beam;

import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The block of a beam pipe, laser or optical: one pipe type per block, connected with a wire cutter, with node data
 * every pipe of the family shares. Pipes and items of a family are told apart by their pipe type, as the pipe net
 * framework does. The family's block names its net, tile and renderer, and the blocks its pipes connect to.
 */
public abstract class BlockBeamPipe<PipeType extends Enum<PipeType> & IPipeType<NodeDataType>, NodeDataType,
        WorldPipeNetType extends WorldPipeNet<NodeDataType, ? extends PipeNet<NodeDataType>>>
                                   extends BlockPipe<PipeType, NodeDataType, WorldPipeNetType> {

    private final PipeType pipeType;
    private final NodeDataType properties;

    protected BlockBeamPipe(@NotNull PipeType pipeType, @NotNull NodeDataType properties) {
        this.pipeType = pipeType;
        this.properties = properties;
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_PIPES);
        setHarvestLevel(ToolClasses.WIRE_CUTTER, 1);
    }

    @Override
    public NodeDataType createProperties(@NotNull IPipeTile<PipeType, NodeDataType> pipeTile) {
        PipeType pipeType = pipeTile.getPipeType();
        if (pipeType == null) return getFallbackType();
        return this.pipeType.modifyProperties(properties);
    }

    @Override
    public NodeDataType createItemProperties(@NotNull ItemStack itemStack) {
        BlockBeamPipe<PipeType, NodeDataType, ?> block = getItemBlock(itemStack);
        return block == null ? null : block.properties;
    }

    @Override
    public ItemStack getDropItem(IPipeTile<PipeType, NodeDataType> pipeTile) {
        return new ItemStack(this, 1, pipeType.ordinal());
    }

    @Override
    protected NodeDataType getFallbackType() {
        return properties;
    }

    @Override
    public PipeType getItemPipeType(@NotNull ItemStack itemStack) {
        BlockBeamPipe<PipeType, NodeDataType, ?> block = getItemBlock(itemStack);
        return block == null ? null : block.pipeType;
    }

    @Override
    public void setTileEntityData(@NotNull TileEntityPipeBase<PipeType, NodeDataType> pipeTile,
                                  ItemStack itemStack) {
        pipeTile.setPipeData(this, pipeType);
    }

    @Override
    public void getSubBlocks(@NotNull CreativeTabs itemIn, @NotNull NonNullList<ItemStack> items) {
        items.add(new ItemStack(this, 1, this.pipeType.ordinal()));
    }

    @Override
    protected boolean isPipeTool(@NotNull ItemStack stack) {
        return ToolHelper.isTool(stack, ToolClasses.WIRE_CUTTER);
    }

    @Override
    public boolean canPipesConnect(IPipeTile<PipeType, NodeDataType> selfTile, EnumFacing side,
                                   IPipeTile<PipeType, NodeDataType> sideTile) {
        return isFamilyPipe(selfTile) && isFamilyPipe(sideTile);
    }

    @Override
    public boolean isHoldingPipe(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getHeldItemMainhand();
        return !stack.isEmpty() && getItemBlock(stack) != null;
    }

    private boolean isFamilyPipe(@Nullable IPipeTile<?, ?> pipeTile) {
        return pipeTile instanceof TileEntityBeamPipe<?, ?, ?> pipe && pipe.getPipeTypeClass() == getPipeTypeClass();
    }

    /** The block of a pipe item of this family, or null for any other item. */
    @Nullable
    @SuppressWarnings("unchecked")
    private BlockBeamPipe<PipeType, NodeDataType, ?> getItemBlock(ItemStack stack) {
        if (stack.getItem() instanceof ItemBlockBeamPipe<?, ?> item &&
                item.getBlock() instanceof BlockBeamPipe<?, ?, ?> block &&
                block.getPipeTypeClass() == getPipeTypeClass()) {
            return (BlockBeamPipe<PipeType, NodeDataType, ?>) block;
        }
        return null;
    }
}

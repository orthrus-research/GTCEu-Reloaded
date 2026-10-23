package gregtech.integration.jei.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTUtility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** A preview value: no tile, world, pattern cache or caller-owned NBT is retained. */
final class PreviewBlock {

    final IBlockState state;
    private final MetaTileEntity prototype;
    private final NBTTagCompound data;

    private PreviewBlock(IBlockState state, MetaTileEntity prototype, NBTTagCompound data) {
        this.state = state;
        this.prototype = prototype;
        this.data = data == null ? null : data.copy();
    }

    static PreviewBlock capture(BlockInfo info) {
        if (info.getTileEntity() == null) return new PreviewBlock(info.getBlockState(), null, null);
        if (!(info.getTileEntity() instanceof MetaTileEntityHolder holder) || holder.getMetaTileEntity() == null) {
            throw new IllegalArgumentException("Unsupported preview tile");
        }
        MetaTileEntity machine = holder.getMetaTileEntity();
        MetaTileEntity prototype = machine.getRegistry().getObject(machine.metaTileEntityId);
        if (prototype == null || prototype.getClass() != machine.getClass()) {
            throw new IllegalArgumentException("Unregistered preview machine");
        }
        return new PreviewBlock(info.getBlockState(), prototype, machine.writeToNBT(new NBTTagCompound()));
    }

    BlockInfo instantiate() {
        if (prototype == null) return new BlockInfo(state);
        return new BlockInfo(state, PreviewTileEntityHolder.copyOf(prototype, data));
    }

    PreviewBlock withFacingOf(PreviewBlock previous) {
        if (prototype == null || previous.data == null) return this;
        EnumFacing facing = EnumFacing.byIndex(previous.data.getInteger("FrontFacing"));
        if (prototype.getFrontFacing() != facing && !prototype.isValidFrontFacing(facing)) return this;
        NBTTagCompound copy = data.copy();
        copy.setInteger("FrontFacing", facing.getIndex());
        return new PreviewBlock(state, prototype, copy);
    }

    PreviewBlock withGeneratedControllerOrientation() {
        if (!isController()) return this;
        // BlockPattern.getPreview generates offsets with NORTH and an unrotated UP axis. Matching receives
        // front.getOpposite(); the controller representation of that same frame is SOUTH with upwards NORTH.
        if (prototype.getFrontFacing() != EnumFacing.SOUTH && !prototype.isValidFrontFacing(EnumFacing.SOUTH)) {
            throw new IllegalArgumentException("Unsupported generated preview orientation");
        }
        NBTTagCompound copy = data.copy();
        copy.setInteger("FrontFacing", EnumFacing.SOUTH.getIndex());
        copy.setByte("UpwardsFacing", (byte) EnumFacing.NORTH.getIndex());
        copy.setBoolean("IsFlipped", false);
        return new PreviewBlock(state, prototype, copy);
    }

    boolean samePart(PreviewBlock other) {
        return state == other.state && prototype == other.prototype;
    }

    boolean isController() {
        return prototype instanceof MultiblockControllerBase;
    }

    ItemStack stack() {
        return prototype == null ? GTUtility.toItem(state) : prototype.getStackForm();
    }
}

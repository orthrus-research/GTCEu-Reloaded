package gregtech.integration.jei.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;

import net.minecraft.nbt.NBTTagCompound;

/** Checks the clone before attaching it, so a broken opt-in factory cannot reparent its prototype. */
final class PreviewTileEntityHolder extends MetaTileEntityHolder {

    static PreviewTileEntityHolder copyOf(MetaTileEntity prototype, NBTTagCompound data) {
        PreviewTileEntityHolder holder = new PreviewTileEntityHolder();
        MetaTileEntity copy = prototype.createMetaTileEntity(holder);
        if (copy == null || copy == prototype || copy.getClass() != prototype.getClass() ||
                (copy.getHolder() != null && copy.getHolder() != holder)) {
            throw new IllegalArgumentException("Preview factory must return an independent machine");
        }
        holder.setRawMetaTileEntity(copy);
        try {
            copy.readFromNBT(data.copy());
        } catch (RuntimeException exception) {
            try {
                holder.invalidate();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
        return holder;
    }
}

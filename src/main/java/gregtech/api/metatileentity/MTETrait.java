package gregtech.api.metatileentity;

import gregtech.api.metatileentity.interfaces.ISyncedTileEntity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public abstract class MTETrait implements ISyncedTileEntity {

    protected final MetaTileEntity metaTileEntity;
    private final int networkId;

    /**
     * Create a new MTE trait.
     *
     * @param metaTileEntity the MTE to reference, and add the trait to
     */
    public MTETrait(@NotNull MetaTileEntity metaTileEntity) {
        this.metaTileEntity = metaTileEntity;

        // derived from the name, so client and server agree whatever order traits are first built in, and no
        // shared table is written from several threads
        this.networkId = getName().hashCode();
        metaTileEntity.addMetaTileEntityTrait(this);
    }

    @NotNull
    public MetaTileEntity getMetaTileEntity() {
        return metaTileEntity;
    }

    /**
     * @return the name of the MTE Trait
     */
    @NotNull
    public abstract String getName();

    /**
     * @return the network ID of the MTE Trait
     */
    public final int getNetworkID() {
        return this.networkId;
    }

    public abstract <T> T getCapability(Capability<T> capability);

    public void onFrontFacingSet(EnumFacing newFrontFacing) {}

    public void update() {}

    @NotNull
    public NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }

    public void deserializeNBT(@NotNull NBTTagCompound compound) {}

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {}

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {}

    @Override
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {}

    @Override
    public final void writeCustomData(int discriminator, @NotNull Consumer<@NotNull PacketBuffer> dataWriter) {
        metaTileEntity.writeTraitData(this, discriminator, dataWriter);
    }

    @Override
    public String toString() {
        return "MTETrait{" +
                "metaTileEntity=" + metaTileEntity +
                ", networkId=" + networkId +
                ", name='" + getName() + '\'' +
                '}';
    }
}

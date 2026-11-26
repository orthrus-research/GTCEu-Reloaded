package gregtech.common.pipelike.laser.tile;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;
import gregtech.common.pipelike.laser.LaserPipeProperties;
import gregtech.common.pipelike.laser.LaserPipeType;
import gregtech.common.pipelike.laser.net.LaserNetHandler;
import gregtech.common.pipelike.laser.net.LaserPipeNet;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.Nullable;

public class TileEntityLaserPipe extends TileEntityBeamPipe<LaserPipeType, LaserPipeProperties, LaserPipeNet> {

    // the LaserNetHandler can only be created on the server, so we have an empty placeholder for the client
    private final ILaserContainer clientCapability = new DefaultLaserContainer();

    @Override
    public Class<LaserPipeType> getPipeTypeClass() {
        return LaserPipeType.class;
    }

    @Override
    protected LaserNetHandler createHandler(LaserPipeNet net, @Nullable EnumFacing facing) {
        return new LaserNetHandler(net, this, facing);
    }

    @Override
    protected int getActiveDataCode() {
        return GregtechDataCodes.PIPE_LASER_ACTIVE;
    }

    /** A laser pipe saves its lit state and sends it with its chunk, so it stays lit while it is fed. */
    @Override
    protected boolean syncsActiveState() {
        return true;
    }

    /** A laser line runs straight: a pipe connects only along the axis it is already connected on. */
    @Override
    protected boolean acceptsConnection(IPipeTile<?, ?> pipe, EnumFacing side) {
        int connections = pipe.getConnections();
        connections &= ~(1 << side.getIndex());
        connections &= ~(1 << side.getOpposite().getIndex());
        return connections == 0;
    }

    @Override
    public <T> T getCapabilityInternal(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechTileCapabilities.CAPABILITY_LASER) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CAPABILITY_LASER.cast(clientCapability);
            }

            return GregtechTileCapabilities.CAPABILITY_LASER.cast((LaserNetHandler) getNetHandler(facing));
        }
        return super.getCapabilityInternal(capability, facing);
    }

    public LaserPipeNet getLaserPipeNet() {
        return getPipeNet();
    }

    private static class DefaultLaserContainer implements ILaserContainer {

        @Override
        public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean inputsEnergy(EnumFacing side) {
            return false;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            return 0;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            return 0;
        }

        @Override
        public long getInputAmperage() {
            return 0;
        }

        @Override
        public long getInputVoltage() {
            return 0;
        }
    }
}

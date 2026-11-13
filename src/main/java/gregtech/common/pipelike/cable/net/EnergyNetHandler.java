package gregtech.common.pipelike.cable.net;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.util.EnumFacing;

import java.util.Objects;

public class EnergyNetHandler implements IEnergyContainer {

    private EnergyNet net;
    private boolean transfer;
    private final TileEntityCable cable;
    private final EnumFacing facing;

    public EnergyNetHandler(EnergyNet net, TileEntityCable cable, EnumFacing facing) {
        this.net = Objects.requireNonNull(net);
        this.cable = Objects.requireNonNull(cable);
        this.facing = facing;
    }

    public void updateNetwork(EnergyNet net) {
        this.net = net;
    }

    public EnergyNet getNet() {
        return net;
    }

    @Override
    public long getInputPerSec() {
        return refreshNetwork() ? net.getEnergyFluxPerSec() : 0;
    }

    @Override
    public long getOutputPerSec() {
        return refreshNetwork() ? net.getEnergyFluxPerSec() : 0;
    }

    @Override
    public long getEnergyCanBeInserted() {
        return transfer ? 0 : getEnergyCapacity();
    }

    @Override
    public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
        if (transfer || voltage <= 0 || amperage <= 0 || !refreshNetwork()) return 0;
        if (side == null) {
            if (facing == null) return 0;
            side = facing;
        }

        long amperesUsed = 0L;
        for (EnergyRoutePath path : net.getNetData(cable.getPos())) {
            if (path.getMaxLoss() >= voltage) {
                // Will lose all the energy with this path, so don't use it
                continue;
            }

            if (GTUtility.arePosEqual(cable.getPos(), path.getTargetPipePos()) && side == path.getTargetFacing()) {
                // Do not insert into source handler
                continue;
            }

            IEnergyContainer dest = path.getHandler();
            if (dest == null) continue;

            EnumFacing facing = path.getTargetFacing().getOpposite();
            if (!dest.inputsEnergy(facing) || dest.getEnergyCanBeInserted() <= 0) continue;

            long pathVoltage = voltage - path.getMaxLoss();
            boolean cableBroken = false;
            for (TileEntityCable cable : path.getPath()) {
                if (cable.getMaxVoltage() < voltage) {
                    // a custom cable limit can sit in the same tier as the voltage; log(0) would cool it
                    int tierDifference = Math.max(1,
                            GTUtility.getTierByVoltage(voltage) - GTUtility.getTierByVoltage(cable.getMaxVoltage()));
                    int heat = (int) (Math.log(tierDifference) * 45 + 36.5);
                    cable.applyHeat(heat);

                    cableBroken = cable.isInvalid();
                    if (cableBroken) {
                        // a cable burned away (or insulation melted)
                        break;
                    }

                    // limit transfer to cables max and void rest
                    pathVoltage = Math.min(cable.getMaxVoltage(), pathVoltage);
                }
            }

            if (cableBroken) continue;

            long amps;
            transfer = true;
            try {
                amps = dest.acceptEnergyFromNetwork(facing, pathVoltage, amperage - amperesUsed);
            } finally {
                transfer = false;
            }
            amps = Math.max(0, Math.min(amps, amperage - amperesUsed));
            if (amps == 0) continue;

            amperesUsed += amps;
            long voltageTraveled = voltage;
            for (TileEntityCable cable : path.getPath()) {
                voltageTraveled -= cable.getNodeData().getLossPerBlock();
                if (voltageTraveled <= 0) break;

                if (!cable.isInvalid()) {
                    cable.incrementAmperage(amps, voltageTraveled);
                }
            }

            if (amperage == amperesUsed) break;
        }

        net.addEnergyFluxPerSec(amperesUsed * voltage);
        return amperesUsed;
    }

    private boolean refreshNetwork() {
        if (cable.isInvalid() || !cable.hasWorld() || cable.getWorld().isRemote ||
                !cable.getWorld().isBlockLoaded(cable.getPos()) ||
                cable.getWorld().getTileEntity(cable.getPos()) != cable) {
            return false;
        }
        this.net = cable.getEnergyNet();
        return net != null && net.isValid() && net.containsNode(cable.getPos());
    }

    @Override
    public long getInputAmperage() {
        return cable.getNodeData().getAmperage();
    }

    @Override
    public long getInputVoltage() {
        return cable.getNodeData().getVoltage();
    }

    @Override
    public long getEnergyCapacity() {
        return getInputVoltage() * getInputAmperage();
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        GTLog.logger.fatal("Do not use changeEnergy() for cables! Use acceptEnergyFromNetwork()");
        if (getInputAmperage() <= 0 || getInputVoltage() <= 0) return 0;
        return acceptEnergyFromNetwork(facing == null ? EnumFacing.UP : facing,
                energyToAdd / getInputAmperage(),
                energyToAdd / getInputVoltage()) * getInputVoltage();
    }

    @Override
    public boolean outputsEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public boolean inputsEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public long getEnergyStored() {
        return 0;
    }

    @Override
    public boolean isOneProbeHidden() {
        return true;
    }
}

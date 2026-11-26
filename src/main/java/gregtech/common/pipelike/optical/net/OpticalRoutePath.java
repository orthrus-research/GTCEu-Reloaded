package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalDataAccessHatch;
import gregtech.common.pipelike.beam.net.BeamRoutePath;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;

public class OpticalRoutePath extends BeamRoutePath<TileEntityOpticalPipe> {

    public OpticalRoutePath(TileEntityOpticalPipe targetPipe, EnumFacing faceToHandler, int distance) {
        super(targetPipe, faceToHandler, distance);
    }

    @Nullable
    public IOpticalDataAccessHatch getDataHatch() {
        IDataAccessHatch dataAccessHatch = getTargetCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS);
        return dataAccessHatch instanceof IOpticalDataAccessHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public IOpticalComputationProvider getComputationHatch() {
        return getTargetCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER);
    }
}

package gregtech.common.pipelike.optical.tile;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.recipes.Recipe;
import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;
import gregtech.common.pipelike.optical.OpticalPipeProperties;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.OpticalNetHandler;
import gregtech.common.pipelike.optical.net.OpticalPipeNet;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TileEntityOpticalPipe extends TileEntityBeamPipe<OpticalPipeType, OpticalPipeProperties, OpticalPipeNet> {

    // the OpticalNetHandler can only be created on the server, so we have an empty placeholder for the client
    private final IDataAccessHatch clientDataHandler = new DefaultDataHandler();
    private final IOpticalComputationProvider clientComputationHandler = new DefaultComputationHandler();

    @Override
    public Class<OpticalPipeType> getPipeTypeClass() {
        return OpticalPipeType.class;
    }

    @Override
    protected OpticalNetHandler createHandler(OpticalPipeNet net, @Nullable EnumFacing facing) {
        return new OpticalNetHandler(net, this, facing);
    }

    @Override
    protected int getActiveDataCode() {
        return GregtechDataCodes.PIPE_OPTICAL_ACTIVE;
    }

    /** An optical cable joins at most two pipes or endpoints, at any angle. */
    @Override
    protected boolean acceptsConnection(IPipeTile<?, ?> pipe, EnumFacing side) {
        return pipe.getNumConnections() < 2;
    }

    @Nullable
    @Override
    public <T> T getCapabilityInternal(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechTileCapabilities.CAPABILITY_DATA_ACCESS) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CAPABILITY_DATA_ACCESS.cast(clientDataHandler);
            }

            return GregtechTileCapabilities.CAPABILITY_DATA_ACCESS.cast((OpticalNetHandler) getNetHandler(facing));
        }

        if (capability == GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER.cast(clientComputationHandler);
            }

            return GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER
                    .cast((OpticalNetHandler) getNetHandler(facing));
        }
        return super.getCapabilityInternal(capability, facing);
    }

    public OpticalPipeNet getOpticalPipeNet() {
        return getPipeNet();
    }

    private static class DefaultDataHandler implements IDataAccessHatch {

        @Override
        public boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private static class DefaultComputationHandler implements IOpticalComputationProvider {

        @Override
        public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
            return 0;
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            return 0;
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            return false;
        }
    }
}

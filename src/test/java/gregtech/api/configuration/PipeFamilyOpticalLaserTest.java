package gregtech.api.configuration;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.common.pipelike.laser.LaserPipeType;
import gregtech.common.pipelike.laser.net.LaserNetHandler;
import gregtech.common.pipelike.laser.net.LaserPipeNet;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.OpticalNetHandler;
import gregtech.common.pipelike.optical.net.OpticalPipeNet;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class PipeFamilyOpticalLaserTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
    }

    @Test
    void laserOppositeFacesKeepIndependentTargetsRegardlessOfWarmOrder() {
        for (boolean eastFirst : new boolean[] { false, true }) {
            ConfigurationTestWorld world = new ConfigurationTestWorld();
            TileEntityLaserPipe pipe = PipeFamilyTestSupport.laser(world, POS);
            laserEndpoint(world, POS.west(), 11);
            laserEndpoint(world, POS.east(), 22);
            pipe.setConnection(EnumFacing.WEST, true, false);
            pipe.setConnection(EnumFacing.EAST, true, false);
            LaserPipeNet net = pipe.getLaserPipeNet();
            if (eastFirst) assertEquals(EnumFacing.WEST, net.getNetData(POS, EnumFacing.EAST).getTargetFacing());

            assertEquals(EnumFacing.EAST, net.getNetData(POS, EnumFacing.WEST).getTargetFacing());
            assertEquals(EnumFacing.WEST, net.getNetData(POS, EnumFacing.EAST).getTargetFacing());
        }
    }

    @Test
    void opticalOppositeFacesKeepIndependentProvidersRegardlessOfWarmOrder() {
        for (boolean eastFirst : new boolean[] { false, true }) {
            ConfigurationTestWorld world = new ConfigurationTestWorld();
            TileEntityOpticalPipe pipe = PipeFamilyTestSupport.optical(world, POS);
            opticalEndpoint(world, POS.west(), 11);
            opticalEndpoint(world, POS.east(), 22);
            pipe.setConnection(EnumFacing.WEST, true, false);
            pipe.setConnection(EnumFacing.EAST, true, false);
            OpticalPipeNet net = pipe.getOpticalPipeNet();
            if (eastFirst) assertEquals(EnumFacing.WEST, net.getNetData(POS, EnumFacing.EAST).getTargetFacing());

            assertEquals(EnumFacing.EAST, net.getNetData(POS, EnumFacing.WEST).getTargetFacing());
            assertEquals(EnumFacing.WEST, net.getNetData(POS, EnumFacing.EAST).getTargetFacing());
        }
    }

    @Test
    void laserRetainedHandleRebindsAfterSplitAndReconnect() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe source = PipeFamilyTestSupport.laser(world, POS);
        TileEntityLaserPipe output = PipeFamilyTestSupport.laser(world, POS.east());
        laserEndpoint(world, POS.east(2), 22);
        source.setConnection(EnumFacing.WEST, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        output.setConnection(EnumFacing.EAST, true, false);
        ILaserContainer retained = source.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, EnumFacing.WEST);
        assertNotNull(retained);
        assertEquals(22, retained.getEnergyStored());
        LaserPipeNet before = ((LaserNetHandler) retained).getNet();

        source.setConnection(EnumFacing.EAST, false, false);
        assertEquals(0, retained.getEnergyStored());
        assertNotSame(before, ((LaserNetHandler) retained).getNet());
        source.setConnection(EnumFacing.EAST, true, false);

        assertEquals(22, retained.getEnergyStored());
    }

    @Test
    void opticalRetainedHandleRebindsAfterSplitAndReconnect() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityOpticalPipe source = PipeFamilyTestSupport.optical(world, POS);
        TileEntityOpticalPipe output = PipeFamilyTestSupport.optical(world, POS.east());
        opticalEndpoint(world, POS.east(2), 22);
        source.setConnection(EnumFacing.WEST, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        output.setConnection(EnumFacing.EAST, true, false);
        IOpticalComputationProvider retained = source.getCapability(
                GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, EnumFacing.WEST);
        assertNotNull(retained);
        assertEquals(22, retained.getMaxCWUt(new ArrayList<>()));
        OpticalPipeNet before = ((OpticalNetHandler) retained).getNet();

        source.setConnection(EnumFacing.EAST, false, false);
        assertEquals(0, retained.getMaxCWUt(new ArrayList<>()));
        assertNotSame(before, ((OpticalNetHandler) retained).getNet());
        source.setConnection(EnumFacing.EAST, true, false);

        assertEquals(22, retained.getMaxCWUt(new ArrayList<>()));
    }

    @Test
    void laserTransferredHandlerFollowsNewOwningTile() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe original = PipeFamilyTestSupport.laser(world, POS);
        laserEndpoint(world, POS.east(), 22);
        original.setConnection(EnumFacing.WEST, true, false);
        original.setConnection(EnumFacing.EAST, true, false);
        ILaserContainer retained = original.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, EnumFacing.WEST);
        assertNotNull(retained);
        TileEntityLaserPipe replacement = new TileEntityLaserPipe();
        world.put(POS, replacement, world.getBlockState(POS));
        replacement.setPipeData(original.getPipeBlock(), LaserPipeType.NORMAL);

        replacement.transferDataFrom(original);

        assertSame(retained, replacement.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, EnumFacing.WEST));
        assertEquals(22, retained.getEnergyStored());
    }

    @Test
    void opticalTransferredHandlerFollowsNewOwningTile() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityOpticalPipe original = PipeFamilyTestSupport.optical(world, POS);
        opticalEndpoint(world, POS.east(), 22);
        original.setConnection(EnumFacing.WEST, true, false);
        original.setConnection(EnumFacing.EAST, true, false);
        IOpticalComputationProvider retained = original.getCapability(
                GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, EnumFacing.WEST);
        assertNotNull(retained);
        TileEntityOpticalPipe replacement = new TileEntityOpticalPipe();
        world.put(POS, replacement, world.getBlockState(POS));
        replacement.setPipeData(original.getPipeBlock(), OpticalPipeType.NORMAL);

        replacement.transferDataFrom(original);

        assertSame(retained, replacement.getCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER,
                EnumFacing.WEST));
        assertEquals(22, retained.getMaxCWUt(new ArrayList<>()));
    }

    @Test
    void opticalNegativeCacheRecoversAndSeenProviderIsNotReentered() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityOpticalPipe pipe = PipeFamilyTestSupport.optical(world, POS);
        PipeTestComputationProvider provider = new PipeTestComputationProvider(22);
        PipeTestEndpoint endpoint = new PipeTestEndpoint().provide(
                GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, provider);
        endpoint.enabled = false;
        world.put(POS.east(), endpoint, Blocks.CHEST.getDefaultState());
        pipe.setConnection(EnumFacing.WEST, true, false);
        pipe.setConnection(EnumFacing.EAST, true, false);
        OpticalPipeNet net = pipe.getOpticalPipeNet();
        assertNull(net.getNetData(POS, EnumFacing.WEST));
        endpoint.enabled = true;
        world.setTotalWorldTime(199);
        assertNull(net.getNetData(POS, EnumFacing.WEST));
        world.setTotalWorldTime(200);
        assertNotNull(net.getNetData(POS, EnumFacing.WEST));
        IOpticalComputationProvider retained = pipe.getCapability(
                GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, EnumFacing.WEST);
        assertNotNull(retained);

        assertEquals(22, retained.getMaxCWUt(new ArrayList<>()));
        assertEquals(0, retained.getMaxCWUt(Collections.singletonList(provider)));
    }

    @Test
    void laserNegativeCacheRecoversAndNullIngressIsQuiet() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe pipe = PipeFamilyTestSupport.laser(world, POS);
        PipeTestEndpoint endpoint = laserEndpoint(world, POS.east(), 22);
        endpoint.enabled = false;
        pipe.setConnection(EnumFacing.WEST, true, false);
        pipe.setConnection(EnumFacing.EAST, true, false);
        LaserPipeNet net = pipe.getLaserPipeNet();
        assertNull(net.getNetData(POS, EnumFacing.WEST));
        endpoint.enabled = true;
        world.setTotalWorldTime(199);
        assertNull(net.getNetData(POS, EnumFacing.WEST));
        world.setTotalWorldTime(200);

        assertNotNull(net.getNetData(POS, EnumFacing.WEST));
        assertNull(net.getNetData(POS, null));
        ILaserContainer unsided = pipe.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, null);
        if (unsided != null) assertEquals(0, unsided.getEnergyStored());
    }

    private static PipeTestEndpoint laserEndpoint(ConfigurationTestWorld world, BlockPos pos, long stored) {
        PipeTestEnergyContainer sink = new PipeTestEnergyContainer();
        sink.stored = stored;
        PipeTestEndpoint endpoint = new PipeTestEndpoint().provide(GregtechTileCapabilities.CAPABILITY_LASER, sink);
        world.put(pos, endpoint, Blocks.CHEST.getDefaultState());
        return endpoint;
    }

    private static void opticalEndpoint(ConfigurationTestWorld world, BlockPos pos, int capacity) {
        world.put(pos, new PipeTestEndpoint().provide(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER,
                new PipeTestComputationProvider(capacity)), Blocks.CHEST.getDefaultState());
    }
}

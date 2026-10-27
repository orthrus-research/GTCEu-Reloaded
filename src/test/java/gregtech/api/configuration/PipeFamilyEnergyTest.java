package gregtech.api.configuration;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.common.pipelike.cable.net.EnergyNet;
import gregtech.common.pipelike.cable.net.EnergyNetHandler;
import gregtech.common.pipelike.cable.net.EnergyRoutePath;
import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PipeFamilyEnergyTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
    }

    @Test
    void destinationExceptionDoesNotPermanentlyLatchTransferGuard() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        PipeTestEnergyContainer sink = new PipeTestEnergyContainer();
        sink.throwNext = true;
        endpoint(world, POS.east(), sink);
        source.setConnection(EnumFacing.WEST, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        IEnergyContainer retained = source.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                EnumFacing.WEST);
        assertNotNull(retained);

        assertThrows(IllegalStateException.class, () -> retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(1, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));

        assertEquals(2, sink.calls);
        assertEquals(31, sink.lastVoltage);
    }

    @Test
    void misreportedAcceptedAmperageIsClampedToOfferedRange() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        PipeTestEnergyContainer sink = new PipeTestEnergyContainer();
        endpoint(world, POS.east(), sink);
        source.setConnection(EnumFacing.WEST, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        IEnergyContainer retained = source.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                EnumFacing.WEST);
        assertNotNull(retained);
        sink.returnedAmps = -10;

        assertEquals(0, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 2));
        sink.returnedAmps = 7;
        assertEquals(2, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 2));
    }

    @Test
    void retainedCapabilityRebindsAcrossSplitAndReconnectWithoutReacquisition() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        PipeFamilyTestSupport.cable(world, POS.east());
        TileEntityCable output = PipeFamilyTestSupport.cable(world, POS.east(2));
        PipeTestEnergyContainer sink = new PipeTestEnergyContainer();
        endpoint(world, POS.east(3), sink);
        source.setConnection(EnumFacing.WEST, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        ((TileEntityCable) world.getTileEntity(POS.east())).setConnection(EnumFacing.EAST, true, false);
        output.setConnection(EnumFacing.EAST, true, false);
        IEnergyContainer retained = source.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                EnumFacing.WEST);
        assertNotNull(retained);
        assertEquals(1, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        EnergyNet before = ((EnergyNetHandler) retained).getNet();

        source.setConnection(EnumFacing.EAST, false, false);
        assertEquals(0, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertNotSame(before, ((EnergyNetHandler) retained).getNet());
        source.setConnection(EnumFacing.EAST, true, false);
        assertEquals(1, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));

        assertEquals(2, sink.calls);
    }

    @Test
    void branchRoutesPreserveDistanceOrderPathContentsAndLosses() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        TileEntityCable near = PipeFamilyTestSupport.cable(world, POS.south());
        TileEntityCable east = PipeFamilyTestSupport.cable(world, POS.east());
        TileEntityCable far = PipeFamilyTestSupport.cable(world, POS.east(2));
        endpoint(world, POS.south(2), new PipeTestEnergyContainer());
        endpoint(world, POS.east(3), new PipeTestEnergyContainer());
        source.setConnection(EnumFacing.SOUTH, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        near.setConnection(EnumFacing.SOUTH, true, false);
        east.setConnection(EnumFacing.EAST, true, false);
        far.setConnection(EnumFacing.EAST, true, false);

        List<EnergyRoutePath> routes = source.getEnergyNet().getNetData(POS);

        assertEquals(2, routes.size());
        assertArrayEquals(new TileEntityCable[] { source, near }, routes.get(0).getPath());
        assertEquals(2, routes.get(0).getDistance());
        assertEquals(2, routes.get(0).getMaxLoss());
        assertArrayEquals(new TileEntityCable[] { source, east, far }, routes.get(1).getPath());
        assertEquals(3, routes.get(1).getDistance());
        assertEquals(3, routes.get(1).getMaxLoss());
    }

    @Test
    void equalDistanceRoutesRetainFacingEncounterOrder() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        TileEntityCable north = PipeFamilyTestSupport.cable(world, POS.north());
        TileEntityCable south = PipeFamilyTestSupport.cable(world, POS.south());
        endpoint(world, POS.north(2), new PipeTestEnergyContainer());
        endpoint(world, POS.south(2), new PipeTestEnergyContainer());
        source.setConnection(EnumFacing.NORTH, true, false);
        source.setConnection(EnumFacing.SOUTH, true, false);
        north.setConnection(EnumFacing.NORTH, true, false);
        south.setConnection(EnumFacing.SOUTH, true, false);

        List<EnergyRoutePath> routes = source.getEnergyNet().getNetData(POS);

        assertEquals(2, routes.size());
        assertSame(north, routes.get(0).getTargetPipe());
        assertSame(south, routes.get(1).getTargetPipe());
    }

    @Test
    void negativeEndpointDiscoveryRecoversWithinTwoHundredTicks() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        PipeTestEndpoint endpoint = endpoint(world, POS.east(), new PipeTestEnergyContainer());
        endpoint.enabled = false;
        source.setConnection(EnumFacing.EAST, true, false);
        EnergyNet net = source.getEnergyNet();
        assertTrue(net.getNetData(POS).isEmpty());

        endpoint.enabled = true;
        world.setTotalWorldTime(199);
        assertTrue(net.getNetData(POS).isEmpty());
        world.setTotalWorldTime(200);

        assertEquals(1, net.getNetData(POS).size());
    }

    @Test
    void replacedSourceTileCannotKeepUsingItsOldCapability() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable source = PipeFamilyTestSupport.cable(world, POS);
        PipeTestEnergyContainer sink = new PipeTestEnergyContainer();
        endpoint(world, POS.east(), sink);
        source.setConnection(EnumFacing.WEST, true, false);
        source.setConnection(EnumFacing.EAST, true, false);
        IEnergyContainer retained = source.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                EnumFacing.WEST);
        assertNotNull(retained);
        world.put(POS, new TileEntity() {}, Blocks.AIR.getDefaultState());

        assertEquals(0, retained.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(0, sink.calls);
    }

    @Test
    void longStraightRouteMaterializesEveryCableInOrder() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityCable[] cables = new TileEntityCable[128];
        for (int i = 0; i < cables.length; i++) cables[i] = PipeFamilyTestSupport.cable(world, POS.east(i));
        endpoint(world, POS.east(cables.length), new PipeTestEnergyContainer());
        for (TileEntityCable cable : cables) cable.setConnection(EnumFacing.EAST, true, false);

        List<EnergyRoutePath> routes = cables[0].getEnergyNet().getNetData(POS);

        assertEquals(1, routes.size());
        assertArrayEquals(cables, routes.get(0).getPath());
        assertEquals(cables.length, routes.get(0).getDistance());
        assertEquals(cables.length, routes.get(0).getMaxLoss());
    }

    private static PipeTestEndpoint endpoint(ConfigurationTestWorld world, BlockPos pos, PipeTestEnergyContainer sink) {
        PipeTestEndpoint endpoint = new PipeTestEndpoint().provide(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                sink);
        world.put(pos, endpoint, Blocks.CHEST.getDefaultState());
        return endpoint;
    }
}

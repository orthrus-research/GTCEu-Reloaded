package gregtech.api.configuration;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.network.PacketDataList;
import gregtech.api.util.TaskScheduler;
import gregtech.common.pipelike.beam.net.BeamPipeNet;
import gregtech.common.pipelike.beam.net.BeamRoutePath;
import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Laser and optical pipes share the beam pipe bases and keep their own line shape, glow timing and saved data. */
public class PipeFamilyBeamTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
    }

    @ParameterizedTest
    @ValueSource(strings = { "laser", "optical" })
    void aLineIsOneNetThatRoutesFromItsOpenEndToTheFarEndpoint(String family) {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityBeamPipe<?, ?, ?>[] line = line(world, family);
        BeamPipeNet<?, ?> net = line[0].getPipeNet();
        assertNotNull(net);
        for (TileEntityBeamPipe<?, ?, ?> pipe : line) {
            assertSame(net, pipe.getPipeNet());
        }
        assertEquals(line.length, net.getAllNodes().size());

        BeamRoutePath<?> route = net.getNetData(POS, EnumFacing.WEST);
        assertNotNull(route);
        assertSame(line[2], route.getTargetPipe());
        assertEquals(EnumFacing.EAST, route.getTargetFacing());
        assertEquals(3, route.getDistance());
    }

    @Test
    void laserLinesRunStraightAndOpticalCablesJoinTwoNeighbours() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe laser = PipeFamilyTestSupport.laser(world, POS);
        TileEntityLaserPipe turnedLaser = PipeFamilyTestSupport.laser(world, POS.east());
        turnedLaser.setConnection(EnumFacing.NORTH, true, false);
        laser.setConnection(EnumFacing.EAST, true, false);
        assertFalse(laser.isConnected(EnumFacing.EAST), "The pipe beside it would turn");
        laser.setConnection(EnumFacing.WEST, true, false);
        laser.setConnection(EnumFacing.NORTH, true, false);
        assertEquals(sides(EnumFacing.WEST), laser.getConnections());

        TileEntityOpticalPipe optical = PipeFamilyTestSupport.optical(world, POS.south(4));
        TileEntityOpticalPipe fullOptical = PipeFamilyTestSupport.optical(world, POS.south(4).east());
        fullOptical.setConnection(EnumFacing.NORTH, true, false);
        fullOptical.setConnection(EnumFacing.SOUTH, true, false);
        optical.setConnection(EnumFacing.EAST, true, false);
        assertFalse(optical.isConnected(EnumFacing.EAST), "The pipe beside it already joins two");
        optical.setConnection(EnumFacing.WEST, true, false);
        optical.setConnection(EnumFacing.NORTH, true, false);
        optical.setConnection(EnumFacing.SOUTH, true, false);
        assertEquals(sides(EnumFacing.WEST, EnumFacing.NORTH), optical.getConnections());
    }

    @Test
    void aMarkKeepsALaserPipeLitButLetsAnOpticalPipeGoDark() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe laser = PipeFamilyTestSupport.laser(world, POS);
        TileEntityOpticalPipe optical = PipeFamilyTestSupport.optical(world, POS.south(4));
        laser.setActive(true, 100);
        optical.setActive(true, 100);
        tick(world, 60);
        laser.setActive(true, 100);
        optical.setActive(true, 100);
        tick(world, 39);
        assertTrue(laser.isActive());
        assertTrue(optical.isActive());

        tick(world, 1);
        assertTrue(laser.isActive(), "A laser pipe stays lit for the full duration after each mark");
        assertFalse(optical.isActive(), "An optical pipe goes dark when the duration from lighting up ends");
        optical.setActive(true, 100);
        assertTrue(optical.isActive());
        tick(world, 60);
        assertFalse(laser.isActive());
    }

    @ParameterizedTest
    @ValueSource(strings = { "laser", "optical" })
    void aRemovedPipeEndsItsGlowWithoutAnotherUpdate(String family) {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityBeamPipe<?, ?, ?> pipe = line(world, family)[0];
        pipe.setActive(true, 100);
        assertNotNull(pipe.getUpdatePacket());
        pipe.invalidate();

        tick(world, 100);
        assertTrue(pipe.isActive(), "The task leaves a removed pipe alone");
        assertNull(pipe.getUpdatePacket());
    }

    @Test
    void eachFamilyAnnouncesItsLitStateUnderItsOwnDataCode() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe laser = PipeFamilyTestSupport.laser(world, POS);
        TileEntityOpticalPipe optical = PipeFamilyTestSupport.optical(world, POS.south(4));
        laser.setActive(true, 100);
        optical.setActive(true, 100);

        assertEquals(Boolean.TRUE, activeUpdate(laser.getUpdatePacket(), GregtechDataCodes.PIPE_LASER_ACTIVE));
        assertEquals(Boolean.TRUE, activeUpdate(optical.getUpdatePacket(), GregtechDataCodes.PIPE_OPTICAL_ACTIVE));
    }

    @Test
    void onlyALaserPipeSavesItsLitStateAndSendsItWithTheChunk() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityLaserPipe laser = PipeFamilyTestSupport.laser(world, POS);
        TileEntityOpticalPipe optical = PipeFamilyTestSupport.optical(world, POS.south(4));
        laser.setActive(true, 100);
        optical.setActive(true, 100);

        NBTTagCompound laserTag = laser.writeToNBT(new NBTTagCompound());
        assertTrue(laserTag.getBoolean("Active"));
        assertFalse(optical.writeToNBT(new NBTTagCompound()).hasKey("Active"));
        ConfigurationTestWorld loadedWorld = new ConfigurationTestWorld();
        TileEntityLaserPipe loaded = new TileEntityLaserPipe();
        loadedWorld.put(POS, loaded, world.getBlockState(POS));
        loaded.readFromNBT(laserTag);
        assertTrue(loaded.isActive());

        PacketBuffer laserSync = new PacketBuffer(Unpooled.buffer());
        PacketBuffer opticalSync = new PacketBuffer(Unpooled.buffer());
        try {
            laser.writeInitialSyncData(laserSync);
            optical.writeInitialSyncData(opticalSync);
            assertEquals(opticalSync.readableBytes() + 1, laserSync.readableBytes(), "The laser's lit state follows");
            ConfigurationTestWorld clientWorld = new ConfigurationTestWorld();
            TileEntityLaserPipe laserClient = new TileEntityLaserPipe();
            clientWorld.put(POS, laserClient, world.getBlockState(POS));
            laserClient.receiveInitialSyncData(laserSync);
            TileEntityOpticalPipe opticalClient = new TileEntityOpticalPipe();
            clientWorld.put(POS.south(4), opticalClient, world.getBlockState(POS.south(4)));
            opticalClient.receiveInitialSyncData(opticalSync);

            assertTrue(laserClient.isActive());
            assertFalse(opticalClient.isActive());
            assertEquals(0, laserSync.readableBytes());
            assertEquals(0, opticalSync.readableBytes());
        } finally {
            laserSync.release();
            opticalSync.release();
        }
    }

    /** Three pipes from {@link #POS} eastwards, open to the west, with an endpoint east of the last. */
    private static TileEntityBeamPipe<?, ?, ?>[] line(ConfigurationTestWorld world, String family) {
        boolean laser = family.equals("laser");
        TileEntityBeamPipe<?, ?, ?>[] line = new TileEntityBeamPipe<?, ?, ?>[3];
        for (int i = 0; i < line.length; i++) {
            BlockPos pos = POS.east(i);
            line[i] = laser ? PipeFamilyTestSupport.laser(world, pos) : PipeFamilyTestSupport.optical(world, pos);
        }
        PipeTestEndpoint endpoint = new PipeTestEndpoint();
        if (laser) {
            endpoint.provide(GregtechTileCapabilities.CAPABILITY_LASER, new PipeTestEnergyContainer());
        } else {
            endpoint.provide(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER,
                    new PipeTestComputationProvider(22));
        }
        world.put(POS.east(line.length), endpoint, Blocks.CHEST.getDefaultState());
        line[0].setConnection(EnumFacing.WEST, true, false);
        for (TileEntityBeamPipe<?, ?, ?> pipe : line) {
            pipe.setConnection(EnumFacing.EAST, true, false);
        }
        return line;
    }

    private static int sides(EnumFacing... sides) {
        int connections = 0;
        for (EnumFacing side : sides) {
            connections |= 1 << side.getIndex();
        }
        return connections;
    }

    private static void tick(ConfigurationTestWorld world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TaskScheduler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, world));
        }
    }

    /** The lit state a pipe's pending updates announce under the data code, or null if they don't. */
    private static Boolean activeUpdate(SPacketUpdateTileEntity packet, int dataCode) {
        assertNotNull(packet);
        Boolean[] active = new Boolean[1];
        PacketDataList.forEachEncoded(packet.getNbtCompound().getByteArray("d"), (discriminator, data) -> {
            if (discriminator == dataCode) active[0] = data.readBoolean();
        });
        return active[0];
    }
}

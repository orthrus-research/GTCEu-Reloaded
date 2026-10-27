package gregtech.api.configuration;

import gregtech.api.pipenet.Node;
import gregtech.api.util.TaskScheduler;
import gregtech.common.pipelike.laser.BlockLaserPipe;
import gregtech.common.pipelike.laser.LaserPipeProperties;
import gregtech.common.pipelike.laser.LaserPipeType;
import gregtech.common.pipelike.laser.net.WorldLaserPipeNet;
import gregtech.common.pipelike.optical.BlockOpticalPipe;
import gregtech.common.pipelike.optical.OpticalPipeProperties;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PipeDimensionIsolationTest {

    private static final BlockPos POS = new BlockPos(3, 64, 7);
    private static final String OPTICAL_ID = "gregtech.optical_pipe_net";
    private static final String LASER_ID = "gregtech.laser_pipe_net";

    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
    }

    @Test
    void sharedGlobalStorageCannotAliasOpticalOrLaserNetworksAtTheSameCoordinates() {
        MapStorage shared = new MapStorage(null);
        PipeDimensionWorld overworld = world(0, shared);
        PipeDimensionWorld nether = world(-1, shared);
        WorldOpticalPipeNet optical0 = WorldOpticalPipeNet.getWorldPipeNet(overworld);
        WorldOpticalPipeNet opticalN = WorldOpticalPipeNet.getWorldPipeNet(nether);
        WorldLaserPipeNet laser0 = WorldLaserPipeNet.getWorldPipeNet(overworld);
        WorldLaserPipeNet laserN = WorldLaserPipeNet.getWorldPipeNet(nether);
        optical0.addNode(POS, OpticalPipeProperties.INSTANCE, 17, 63, true);
        opticalN.addNode(POS, OpticalPipeProperties.INSTANCE, 29, 0, false);
        laser0.addNode(POS, LaserPipeProperties.INSTANCE, 31, 63, true);
        laserN.addNode(POS, LaserPipeProperties.INSTANCE, 47, 0, false);

        assertNotSame(optical0, opticalN);
        assertNotSame(laser0, laserN);
        assertSame(overworld, optical0.getWorld());
        assertSame(nether, opticalN.getWorld());
        assertSame(overworld, laser0.getWorld());
        assertSame(nether, laserN.getWorld());
        assertEquals(17, optical0.getNetFromPos(POS).getNodeAt(POS).mark);
        assertEquals(29, opticalN.getNetFromPos(POS).getNodeAt(POS).mark);
        assertEquals(31, laser0.getNetFromPos(POS).getNodeAt(POS).mark);
        assertEquals(47, laserN.getNetFromPos(POS).getNodeAt(POS).mark);
        assertSame(optical0, WorldOpticalPipeNet.getWorldPipeNet(overworld));
        assertSame(laserN, WorldLaserPipeNet.getWorldPipeNet(nether));
        assertNull(shared.getOrLoadData(WorldOpticalPipeNet.class, OPTICAL_ID));
        assertNull(shared.getOrLoadData(WorldLaserPipeNet.class, LASER_ID));
    }

    @Test
    void overworldReusesLiteralLegacyFilesAndOtherDimensionsLeaveTheirBytesUntouched() throws IOException {
        PipeDimensionSaveHandler rootFiles = new PipeDimensionSaveHandler(this.directory);
        MapStorage oldGlobal = new MapStorage(rootFiles);
        WorldOpticalPipeNet oldOptical = new WorldOpticalPipeNet(OPTICAL_ID);
        WorldLaserPipeNet oldLaser = new WorldLaserPipeNet(LASER_ID);
        oldGlobal.setData(OPTICAL_ID, oldOptical);
        oldGlobal.setData(LASER_ID, oldLaser);
        oldOptical.addNode(POS, OpticalPipeProperties.INSTANCE, 73, 63, true);
        oldLaser.addNode(POS, LaserPipeProperties.INSTANCE, 91, 63, true);
        oldGlobal.saveAllData();
        byte[] opticalBefore = Files.readAllBytes(rootFiles.getMapFileFromName(OPTICAL_ID).toPath());
        byte[] laserBefore = Files.readAllBytes(rootFiles.getMapFileFromName(LASER_ID).toPath());
        NBTTagCompound oldOpticalGraph = oldOptical.writeToNBT(new NBTTagCompound());
        NBTTagCompound oldLaserGraph = oldLaser.writeToNBT(new NBTTagCompound());

        PipeDimensionWorld overworld = new PipeDimensionWorld(0, oldGlobal, new MapStorage(rootFiles));
        assertEquals(73, WorldOpticalPipeNet.getWorldPipeNet(overworld).getNetFromPos(POS).getNodeAt(POS).mark);
        assertEquals(91, WorldLaserPipeNet.getWorldPipeNet(overworld).getNetFromPos(POS).getNodeAt(POS).mark);
        PipeDimensionSaveHandler netherFiles = new PipeDimensionSaveHandler(this.directory.resolve("DIM-1"));
        PipeDimensionWorld nether = new PipeDimensionWorld(-1, oldGlobal, new MapStorage(netherFiles));
        WorldOpticalPipeNet localOptical = WorldOpticalPipeNet.getWorldPipeNet(nether);
        WorldLaserPipeNet localLaser = WorldLaserPipeNet.getWorldPipeNet(nether);
        assertNull(localOptical.getNetFromPos(POS), "Ambiguous global nodes must not be cloned into this dimension");
        assertNull(localLaser.getNetFromPos(POS));
        RecoverableOpticalPipe optical = optical(nether, POS);
        RecoverableLaserPipe laser = laser(nether, POS.east(5));
        optical.recover(nether, POS);
        laser.recover(nether, POS.east(5));
        nether.getPerWorldStorage().saveAllData();

        assertEquals(oldOpticalGraph, oldOptical.writeToNBT(new NBTTagCompound()));
        assertEquals(oldLaserGraph, oldLaser.writeToNBT(new NBTTagCompound()));
        assertArrayEquals(opticalBefore, Files.readAllBytes(rootFiles.getMapFileFromName(OPTICAL_ID).toPath()));
        assertArrayEquals(laserBefore, Files.readAllBytes(rootFiles.getMapFileFromName(LASER_ID).toPath()));
        assertTrue(netherFiles.getMapFileFromName(OPTICAL_ID).isFile());
        assertTrue(netherFiles.getMapFileFromName(LASER_ID).isFile());
    }

    @Test
    void loadedTileRecoveryUsesLivePropertiesPaintConnectionsAndActivityOnlyWhenMissing() {
        PipeDimensionWorld world = world(-1, new MapStorage(null));
        RecoverableOpticalPipe optical = optical(world, POS);
        RecoverableLaserPipe laser = laser(world, POS.east(5));
        optical.setPaintingColor(0x13579B);
        optical.setConnection(EnumFacing.WEST, true, false);
        optical.setConnection(EnumFacing.EAST, true, false);
        laser.setPaintingColor(0x2468AC);
        laser.setConnection(EnumFacing.NORTH, true, false);
        optical.recover(world, POS);
        laser.recover(world, POS.east(5));

        Node<OpticalPipeProperties> opticalNode = WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS)
                .getNodeAt(POS);
        Node<LaserPipeProperties> laserNode = WorldLaserPipeNet.getWorldPipeNet(world).getNetFromPos(POS.east(5))
                .getNodeAt(POS.east(5));
        assertSame(optical.getNodeData(), opticalNode.data);
        assertSame(laser.getNodeData(), laserNode.data);
        assertEquals(0x13579B, opticalNode.mark);
        assertEquals(0x2468AC, laserNode.mark);
        assertEquals(optical.getConnections(), opticalNode.openConnections);
        assertEquals(laser.getConnections(), laserNode.openConnections);
        assertTrue(opticalNode.isActive);
        assertTrue(laserNode.isActive);
        opticalNode.mark = 1234;
        optical.recover(world, POS);
        assertSame(opticalNode, WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS).getNodeAt(POS));
        assertEquals(1234, opticalNode.mark, "Recovery must not replace existing membership");

        optical.getPipeBlock().updateTick(world, POS, world.getBlockState(POS), world.rand);
        assertEquals(0x13579B, WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS).getNodeAt(POS).mark,
                "Ordinary scheduled placement must preserve a painted tile's actual mark");
    }

    @Test
    void closedUnpaintedTileRecoversItsZeroMarkAndInactiveState() {
        PipeDimensionWorld world = world(1, new MapStorage(null));
        RecoverableOpticalPipe pipe = optical(world, POS);
        pipe.recover(world, POS);
        Node<OpticalPipeProperties> node = WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS).getNodeAt(POS);
        assertEquals(0, node.mark);
        assertEquals(0, node.openConnections);
        assertFalse(node.isActive);
    }

    @Test
    void staleRecoveryTasksCannotReadUnloadedTilesOrRecreateReplacedMovedOrInvalidTiles() {
        PipeDimensionWorld world = world(-1, new MapStorage(null));
        RecoverableOpticalPipe unloaded = optical(world, POS);
        world.unloaded.add(POS);
        unloaded.recover(world, POS);
        world.unloaded.clear();
        assertNull(WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS));

        RecoverableOpticalPipe replaced = optical(world, POS);
        world.put(POS, new RecoverableOpticalPipe(), Blocks.AIR.getDefaultState());
        replaced.recover(world, POS);
        assertNull(WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS));

        RecoverableOpticalPipe moved = optical(world, POS);
        moved.setPos(POS.up());
        moved.recover(world, POS);
        assertNull(WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS));

        RecoverableOpticalPipe invalid = optical(world, POS);
        invalid.invalidate();
        invalid.recover(world, POS);
        assertNull(WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS));

        RecoverableOpticalPipe rebound = optical(world, POS);
        rebound.setWorld(world(1, new MapStorage(null)));
        rebound.recover(world, POS);
        assertNull(WorldOpticalPipeNet.getWorldPipeNet(world).getNetFromPos(POS));
    }

    @Test
    void detachedPreviewOnLoadDoesNotAllocateAServerTask() {
        PipeDimensionWorld preview = world(0, new MapStorage(null));
        RecoverableOpticalPipe pipe = optical(preview, POS);
        assertNull(TaskScheduler.get(preview));
        pipe.onLoad();
        assertNull(TaskScheduler.get(preview));
        assertNull(WorldOpticalPipeNet.getWorldPipeNet(preview).getNetFromPos(POS));
    }

    private static PipeDimensionWorld world(int dimension, MapStorage global) {
        return new PipeDimensionWorld(dimension, global, new MapStorage(null));
    }

    private static RecoverableOpticalPipe optical(PipeDimensionWorld world, BlockPos pos) {
        BlockOpticalPipe block = new BlockOpticalPipe(OpticalPipeType.NORMAL);
        RecoverableOpticalPipe pipe = new RecoverableOpticalPipe();
        world.put(pos, pipe, block.getDefaultState());
        pipe.setPipeData(block, OpticalPipeType.NORMAL);
        return pipe;
    }

    private static RecoverableLaserPipe laser(PipeDimensionWorld world, BlockPos pos) {
        BlockLaserPipe block = new BlockLaserPipe(LaserPipeType.NORMAL);
        RecoverableLaserPipe pipe = new RecoverableLaserPipe();
        world.put(pos, pipe, block.getDefaultState());
        pipe.setPipeData(block, LaserPipeType.NORMAL);
        return pipe;
    }
}

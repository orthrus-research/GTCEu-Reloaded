package gregtech.api.configuration;

import gregtech.api.pipenet.Node;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.TaskScheduler;
import gregtech.api.util.world.DummySaveHandler;
import gregtech.common.pipelike.itempipe.BlockItemPipe;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelike.laser.BlockLaserPipe;
import gregtech.common.pipelike.laser.LaserPipeProperties;
import gregtech.common.pipelike.laser.LaserPipeType;
import gregtech.common.pipelike.laser.net.WorldLaserPipeNet;
import gregtech.common.pipelike.optical.BlockOpticalPipe;
import gregtech.common.pipelike.optical.OpticalPipeProperties;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PipeDimensionIsolationTest {

    private static final BlockPos POS = new BlockPos(3, 64, 7);
    private static final String OPTICAL_ID = "gregtech.optical_pipe_net";
    private static final String LASER_ID = "gregtech.laser_pipe_net";
    private static final String ITEM_ID = "gregtech.item_pipe_net";

    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void identicalItemNodesAndRetainedHandlersSurviveRemovalInAnotherDimension() {
        MapStorage shared = new MapStorage(null);
        PipeDimensionWorld overworld = world(0, shared);
        PipeDimensionWorld nether = world(-1, shared);
        RecoverableItemPipe first = item(overworld, POS);
        RecoverableItemPipe second = item(nether, POS);
        ItemStackHandler firstDestination = new ItemStackHandler(1);
        ItemStackHandler secondDestination = new ItemStackHandler(1);
        overworld.put(POS.east(), new PipeTestEndpoint().provide(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                firstDestination), Blocks.CHEST.getDefaultState());
        nether.put(POS.east(), new PipeTestEndpoint().provide(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                secondDestination), Blocks.CHEST.getDefaultState());
        for (RecoverableItemPipe pipe : new RecoverableItemPipe[] { first, second }) {
            pipe.setConnection(EnumFacing.WEST, true, false);
            pipe.setConnection(EnumFacing.EAST, true, false);
        }
        first.recover(overworld, POS);
        second.recover(nether, POS);
        WorldItemPipeNet firstGraph = WorldItemPipeNet.getWorldPipeNet(overworld);
        WorldItemPipeNet secondGraph = WorldItemPipeNet.getWorldPipeNet(nether);
        assertNotSame(firstGraph, secondGraph);
        Node<ItemPipeProperties> firstNode = firstGraph.getNetFromPos(POS).getNodeAt(POS);
        Node<ItemPipeProperties> secondNode = secondGraph.getNetFromPos(POS).getNodeAt(POS);
        assertNotSame(firstNode, secondNode);
        assertEquals(firstNode.data, secondNode.data);
        assertEquals(firstNode.mark, secondNode.mark);
        assertEquals(firstNode.openConnections, secondNode.openConnections);
        assertEquals(firstNode.isActive, secondNode.isActive);
        assertSame(firstGraph.getNetFromPos(POS), first.getItemPipeNet());
        assertSame(secondGraph.getNetFromPos(POS), second.getItemPipeNet());
        IItemHandler retained = second.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.WEST);
        assertNotNull(retained);

        firstGraph.removeNode(POS);
        WorldItemPipeNet.getWorldPipeNet(overworld);

        assertNull(firstGraph.getNetFromPos(POS));
        assertNotNull(secondGraph.getNetFromPos(POS));
        assertSame(nether, secondGraph.getWorld());
        assertTrue(retained.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).isEmpty());
        assertEquals(1, secondDestination.getStackInSlot(0).getCount());
        assertTrue(firstDestination.getStackInSlot(0).isEmpty());
    }

    @Test
    void nonOverworldItemRecoveryAndSaveNeverLoadOrRewriteTheAmbiguousLegacyRoot() throws IOException {
        PipeDimensionSaveHandler rootFiles = new PipeDimensionSaveHandler(this.directory);
        writeItemRecord(rootFiles, legacyItemRecord(73));
        byte[] rootBefore = Files.readAllBytes(rootFiles.getMapFileFromName(ITEM_ID).toPath());
        PipeDimensionGlobalStorage shared = new PipeDimensionGlobalStorage(rootFiles);
        PipeDimensionSaveHandler localFiles = new PipeDimensionSaveHandler(this.directory.resolve("DIM-1"));
        PipeDimensionWorld nether = new PipeDimensionWorld(-1, shared, new MapStorage(localFiles), rootFiles);
        WorldItemPipeNet graph = WorldItemPipeNet.getWorldPipeNet(nether);
        assertNull(graph.getNetFromPos(POS), "Ambiguous root nodes must not be assigned to this dimension");
        assertEquals(0, shared.itemLoadRequests, "Even a warning must not trigger legacy priority normalization");
        RecoverableItemPipe pipe = item(nether, POS);
        pipe.setPaintingColor(0x13579B);
        pipe.setConnection(EnumFacing.WEST, true, false);
        pipe.recover(nether, POS);
        Node<ItemPipeProperties> node = graph.getNetFromPos(POS).getNodeAt(POS);
        assertSame(pipe.getNodeData(), node.data);
        assertEquals(0x13579B, node.mark);
        assertEquals(pipe.getConnections(), node.openConnections);
        assertTrue(node.isActive);
        pipe.recover(nether, POS);
        assertSame(node, graph.getNetFromPos(POS).getNodeAt(POS));
        nether.getPerWorldStorage().saveAllData();
        shared.saveAllData();

        assertEquals(0, shared.itemLoadRequests);
        assertArrayEquals(rootBefore, Files.readAllBytes(rootFiles.getMapFileFromName(ITEM_ID).toPath()));
        assertTrue(localFiles.getMapFileFromName(ITEM_ID).isFile());
        PipeDimensionWorld restarted = new PipeDimensionWorld(-1, shared, new MapStorage(localFiles), rootFiles);
        Node<ItemPipeProperties> restored = WorldItemPipeNet.getWorldPipeNet(restarted).getNetFromPos(POS)
                .getNodeAt(POS);
        assertEquals(node.data, restored.data);
        assertEquals(node.mark, restored.mark);
        assertEquals(node.openConnections, restored.openConnections);
        assertEquals(node.isActive, restored.isActive);
        assertEquals(0, shared.itemLoadRequests);
        assertArrayEquals(rootBefore, Files.readAllBytes(rootFiles.getMapFileFromName(ITEM_ID).toPath()));
    }

    @Test
    void overworldItemGetterKeepsTheLiteralFileAndNormalizesItsOwnLegacyPriority() throws IOException {
        PipeDimensionSaveHandler rootFiles = new PipeDimensionSaveHandler(this.directory);
        writeItemRecord(rootFiles, legacyItemRecord(73));
        PipeDimensionGlobalStorage shared = new PipeDimensionGlobalStorage(rootFiles);
        PipeDimensionWorld overworld = new PipeDimensionWorld(0, shared, new MapStorage(rootFiles), rootFiles);

        WorldItemPipeNet graph = WorldItemPipeNet.getWorldPipeNet(overworld);

        assertEquals(73, graph.getNetFromPos(POS).getNodeAt(POS).data.getPriority());
        assertTrue(graph.isDirty());
        assertEquals(0, shared.itemLoadRequests);
        overworld.getPerWorldStorage().saveAllData();
        NBTTagCompound properties = itemProperties(readItemRecord(rootFiles));
        assertEquals(73, properties.getInteger("Resistance"));
        assertEquals(73, properties.getInteger("Range"));
        PipeDimensionWorld restarted = new PipeDimensionWorld(0, shared, new MapStorage(rootFiles), rootFiles);
        assertFalse(WorldItemPipeNet.getWorldPipeNet(restarted).isDirty());
        assertEquals(0, shared.itemLoadRequests);
    }

    @Test
    void missingLegacySaveHandlersAndMapFilesDoNotPreventIndependentItemStorage() {
        for (ISaveHandler files : new ISaveHandler[] { null, new DummySaveHandler() }) {
            PipeDimensionGlobalStorage shared = new PipeDimensionGlobalStorage(null);
            PipeDimensionWorld nether = new PipeDimensionWorld(-1, shared, new MapStorage(null), files);
            WorldItemPipeNet graph = WorldItemPipeNet.getWorldPipeNet(nether);
            graph.addNode(POS, new ItemPipeProperties(4096, 0.5f), 0, 0, false);
            assertSame(graph, WorldItemPipeNet.getWorldPipeNet(nether));
            assertEquals(0, shared.itemLoadRequests);
            assertNotNull(graph.getNetFromPos(POS));
        }
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
        RecoverableItemPipe item = item(world, POS.east(5));
        item.recover(world, POS.east(5));
        Node<ItemPipeProperties> itemNode = WorldItemPipeNet.getWorldPipeNet(world).getNetFromPos(POS.east(5))
                .getNodeAt(POS.east(5));
        assertEquals(0, itemNode.mark);
        assertEquals(0, itemNode.openConnections);
        assertFalse(itemNode.isActive);
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

    private static RecoverableItemPipe item(PipeDimensionWorld world, BlockPos pos) {
        BlockItemPipe block = new BlockItemPipe(ItemPipeType.NORMAL, Materials.Tin.getRegistry());
        block.addPipeMaterial(Materials.Tin, new ItemPipeProperties(4096, 0.5f));
        RecoverableItemPipe pipe = new RecoverableItemPipe();
        world.put(pos, pipe, block.getDefaultState());
        pipe.setPipeData(block, ItemPipeType.NORMAL, Materials.Tin);
        return pipe;
    }

    private static NBTTagCompound legacyItemRecord(int priority) {
        WorldItemPipeNet graph = new WorldItemPipeNet(ITEM_ID);
        graph.addNode(POS, new ItemPipeProperties(priority, 0.5f), 0, 48, true);
        NBTTagCompound record = graph.writeToNBT(new NBTTagCompound());
        itemProperties(record).removeTag("Range");
        return record;
    }

    private static NBTTagCompound itemProperties(NBTTagCompound record) {
        return record.getTagList("PipeNets", 10).getCompoundTagAt(0).getCompoundTag("Nodes")
                .getTagList("WireProperties", 10).getCompoundTagAt(0);
    }

    private static void writeItemRecord(PipeDimensionSaveHandler files, NBTTagCompound record) throws IOException {
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("data", record);
        try (FileOutputStream output = new FileOutputStream(files.getMapFileFromName(ITEM_ID))) {
            CompressedStreamTools.writeCompressed(wrapper, output);
        }
    }

    private static NBTTagCompound readItemRecord(PipeDimensionSaveHandler files) throws IOException {
        try (FileInputStream input = new FileInputStream(files.getMapFileFromName(ITEM_ID))) {
            return CompressedStreamTools.readCompressed(input).getCompoundTag("data");
        }
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

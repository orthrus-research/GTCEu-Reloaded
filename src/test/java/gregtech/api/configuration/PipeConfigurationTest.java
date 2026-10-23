package gregtech.api.configuration;

import gregtech.Bootstrap;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.net.WorldFluidPipeNet;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipe;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;
import gregtech.common.pipelike.itempipe.BlockItemPipe;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipe;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipeTickable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class PipeConfigurationTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static BlockItemPipe itemBlock;
    private static BlockFluidPipe fluidBlock;

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
        itemBlock = new BlockItemPipe(ItemPipeType.NORMAL, Materials.Copper.getRegistry());
        itemBlock.addPipeMaterial(Materials.Copper, new ItemPipeProperties(1, 1));
        fluidBlock = new BlockFluidPipe(FluidPipeType.NORMAL, Materials.Copper.getRegistry());
        fluidBlock.addPipeMaterial(Materials.Copper, new FluidPipeProperties(1000, 1000, true, false, false, false));
    }

    @Test
    public void replacesAllSixMasksAndRebuildsReciprocalGraphInBothDirections() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe west = item(world, CENTER.west(), new TileEntityItemPipe());
        TileEntityItemPipe east = item(world, CENTER.east(), new TileEntityItemPipe());
        local.setConnection(EnumFacing.WEST, true, false);
        local.setFaceBlocked(EnumFacing.DOWN, true);
        WorldItemPipeNet graph = WorldItemPipeNet.getWorldPipeNet(world);
        assertSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.west()));
        assertNotSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.east()));

        assertTrue(prepare(world, local, masks(bit(EnumFacing.EAST), bit(EnumFacing.UP))).execute());
        assertEquals(bit(EnumFacing.EAST), local.getConnections());
        assertEquals(bit(EnumFacing.UP), local.getBlockedConnections());
        assertFalse(west.isConnected(EnumFacing.EAST));
        assertTrue(east.isConnected(EnumFacing.WEST));
        assertNotSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.west()));
        assertSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.east()));
        assertTrue(prepare(world, local, masks(0, 0)).execute());
        assertEquals(0, local.getConnections());
        assertEquals(0, local.getBlockedConnections());
        assertFalse(east.isConnected(EnumFacing.WEST));
        assertNotSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.east()));
    }

    @Test
    public void coveredOpenReciprocalEndIsPreservedExactlyLikeWrenchDispatch() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe neighbor = item(world, CENTER.east(), new TileEntityItemPipeTickable());
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("configuration_edge_cover"),
                (def, holder, face) -> new CoverConveyor(def, holder, face, 1, 8), ItemStack.EMPTY);
        neighbor.getCoverableImplementation().addCover(EnumFacing.WEST,
                definition.createCover(neighbor.getCoverableImplementation(), EnumFacing.WEST));
        local.setConnection(EnumFacing.EAST, true, false);
        assertTrue(neighbor.isConnected(EnumFacing.WEST));
        assertTrue(prepare(world, local, masks(0, 0)).execute());
        assertFalse(local.isConnected(EnumFacing.EAST));
        assertTrue(neighbor.isConnected(EnumFacing.WEST));
        // Opening a closed reciprocal end is permitted even when that end has a cover.
        neighbor.setConnection(EnumFacing.WEST, false, true);
        assertTrue(prepare(world, local, masks(bit(EnumFacing.EAST), 0)).execute());
        assertTrue(neighbor.isConnected(EnumFacing.WEST));
    }

    @Test
    public void deniedUnloadedIncompatibleAndUnknownNeighborsRejectWithoutMutation() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe neighbor = item(world, CENTER.east(), new TileEntityItemPipe());
        NBTTagCompound desired = masks(bit(EnumFacing.EAST), bit(EnumFacing.UP));
        ConfigurationContext denied = new ConfigurationContext(world, (pos, face) -> !pos.equals(CENTER.east()));
        assertThrows(IllegalArgumentException.class, () -> local.getConfigurationAdapter().prepare(desired, denied,
                new ConfigurationTransaction()));
        world.unloaded.add(CENTER.east());
        assertThrows(IllegalArgumentException.class, () -> prepare(world, local, desired));
        world.unloaded.clear();
        assertEquals(0, local.getConnections());
        assertEquals(0, local.getBlockedConnections());
        assertEquals(0, neighbor.getConnections());

        ConfigurationTestWorld otherWorld = new ConfigurationTestWorld();
        TileEntityItemPipe item = item(otherWorld, CENTER, new TileEntityItemPipe());
        fluid(otherWorld, CENTER.east(), new TileEntityFluidPipe());
        assertThrows(IllegalArgumentException.class, () -> prepare(otherWorld, item, desired));
        ConfigurationTestWorld addonWorld = new ConfigurationTestWorld();
        TileEntityItemPipe admitted = item(addonWorld, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe unknown = item(addonWorld, CENTER.east(), new TileEntityItemPipe() {});
        assertNull(unknown.getConfigurationAdapter());
        assertThrows(IllegalArgumentException.class, () -> prepare(addonWorld, admitted, desired));
        assertEquals(0, admitted.getConnections());
    }

    @Test
    public void staleReciprocalStateAndReplacedTileAbortPreparedPlan() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe neighbor = item(world, CENTER.east(), new TileEntityItemPipe());
        ConfigurationTransaction stale = prepare(world, local, masks(bit(EnumFacing.EAST), 0));
        neighbor.setFaceBlocked(EnumFacing.UP, true);
        assertFalse(stale.execute());
        assertEquals(0, local.getConnections());
        ConfigurationTransaction replaced = prepare(world, local, masks(bit(EnumFacing.EAST), 0));
        item(world, CENTER.east(), new TileEntityItemPipe());
        assertFalse(replaced.execute());
        assertEquals(0, local.getConnections());
    }

    @Test
    public void permissionAndLoadingLossAfterPreparationAbortBeforeMutation() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe neighbor = item(world, CENTER.east(), new TileEntityItemPipe());
        AtomicBoolean allowed = new AtomicBoolean(true);
        ConfigurationTransaction permissionLost = new ConfigurationTransaction();
        local.getConfigurationAdapter().prepare(masks(bit(EnumFacing.EAST), bit(EnumFacing.UP)),
                new ConfigurationContext(world, (pos, face) -> allowed.get()), permissionLost);
        allowed.set(false);
        assertFalse(permissionLost.execute());
        ConfigurationTransaction unloaded = prepare(world, local, masks(bit(EnumFacing.EAST), bit(EnumFacing.UP)));
        world.unloaded.add(CENTER.east());
        assertFalse(unloaded.execute());
        assertEquals(0, local.getConnections());
        assertEquals(0, local.getBlockedConnections());
        assertEquals(0, neighbor.getConnections());
        assertEquals(0, neighbor.getBlockedConnections());
    }

    @Test
    public void failureAfterGraphMutationRestoresAllEdgesAndGraphReachability() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        TileEntityItemPipe west = item(world, CENTER.west(), new TileEntityItemPipe());
        TileEntityItemPipe east = item(world, CENTER.east(), new TileEntityItemPipe());
        local.setConnection(EnumFacing.WEST, true, false);
        local.setFaceBlocked(EnumFacing.DOWN, true);
        ConfigurationTransaction transaction = prepare(world, local, masks(bit(EnumFacing.EAST), bit(EnumFacing.UP)));
        WorldItemPipeNet graph = WorldItemPipeNet.getWorldPipeNet(world);
        transaction.add(new ConfigurationChange() {

            @Override
            public boolean isCurrent() {
                return true;
            }

            @Override
            public void apply() {
                assertSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.east()));
                throw new IllegalStateException("Injected failure after real graph updates");
            }

            @Override
            public void rollback() {}
        });
        assertFalse(transaction.execute());
        assertEquals(bit(EnumFacing.WEST), local.getConnections());
        assertEquals(bit(EnumFacing.DOWN), local.getBlockedConnections());
        assertTrue(west.isConnected(EnumFacing.EAST));
        assertFalse(east.isConnected(EnumFacing.WEST));
        assertSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.west()));
        assertNotSame(graph.getNetFromPos(CENTER), graph.getNetFromPos(CENTER.east()));
    }

    @Test
    public void fluidPipeTopologyChangesKeepTankContentsAndAcceptBothCoreImplementations() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityFluidPipeTickable local = (TileEntityFluidPipeTickable) fluid(world, CENTER,
                new TileEntityFluidPipeTickable());
        TileEntityFluidPipe neighbor = fluid(world, CENTER.east(), new TileEntityFluidPipe());
        local.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, 731), true);
        Object tank = local.getFluidTanks()[0];
        assertTrue(prepare(world, local, masks(bit(EnumFacing.EAST), bit(EnumFacing.DOWN))).execute());
        assertTrue(neighbor.isConnected(EnumFacing.WEST));
        assertSame(tank, local.getFluidTanks()[0]);
        assertEquals(731, local.getFluidTanks()[0].getFluidAmount());
        assertTrue(prepare(world, local, masks(0, 0)).execute());
        assertEquals(731, local.getFluidTanks()[0].getFluidAmount());
        assertFalse(neighbor.isConnected(EnumFacing.WEST));
        assertNull(new TileEntityFluidPipe() {}.getConfigurationAdapter());
    }

    @Test
    public void rejectsNonIntegerAndOutOfRangeMasks() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        TileEntityItemPipe local = item(world, CENTER, new TileEntityItemPipe());
        for (int value : new int[] { -1, 64, Integer.MAX_VALUE }) {
            assertThrows(IllegalArgumentException.class, () -> prepare(world, local, masks(value, 0)));
            assertThrows(IllegalArgumentException.class, () -> prepare(world, local, masks(0, value)));
        }
        NBTTagCompound wrongType = masks(0, 0);
        wrongType.setLong("Connections", 0);
        assertThrows(IllegalArgumentException.class, () -> prepare(world, local, wrongType));
        assertEquals(0, local.getConnections());
    }

    private static TileEntityItemPipe item(ConfigurationTestWorld world, BlockPos pos, TileEntityItemPipe pipe) {
        world.put(pos, pipe, itemBlock.getDefaultState());
        pipe.setPipeData(itemBlock, ItemPipeType.NORMAL, Materials.Copper);
        WorldItemPipeNet.getWorldPipeNet(world).addNode(pos, pipe.getNodeData(), 0, pipe.getConnections(), false);
        return pipe;
    }

    private static TileEntityFluidPipe fluid(ConfigurationTestWorld world, BlockPos pos, TileEntityFluidPipe pipe) {
        world.put(pos, pipe, fluidBlock.getDefaultState());
        pipe.setPipeData(fluidBlock, FluidPipeType.NORMAL, Materials.Copper);
        WorldFluidPipeNet.getWorldPipeNet(world).addNode(pos, pipe.getNodeData(), 0, pipe.getConnections(), false);
        return pipe;
    }

    private static int bit(EnumFacing side) {
        return 1 << side.getIndex();
    }

    private static NBTTagCompound masks(int connections, int blocked) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Connections", connections);
        data.setInteger("Blocked", blocked);
        return data;
    }

    private static ConfigurationTransaction prepare(ConfigurationTestWorld world, IPipeTile<?, ?> pipe,
                                                    NBTTagCompound data) {
        ConfigurationTransaction transaction = new ConfigurationTransaction();
        pipe.getConfigurationAdapter().prepare(data, new ConfigurationContext(world, (pos, side) -> true), transaction);
        return transaction;
    }
}

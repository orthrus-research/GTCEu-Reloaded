package gregtech.api.configuration;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverItemFilter;
import gregtech.common.covers.ItemFilterMode;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.ItemPipeNet;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipe;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemPipeRouteCacheRegressionTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void oppositeIngressFacesKeepIndependentRoutes() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemPipeNet net = fixture.pipe.getItemPipeNet();
        assertEquals(EnumFacing.EAST, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST)
                .get(0).getTargetFacing());
        assertEquals(EnumFacing.WEST, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.EAST)
                .get(0).getTargetFacing());
        assertNotSame(net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.EAST),
                net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST));
    }

    @Test
    void delayedEndpointAppearsWithinTwoHundredTicksForEmptyCache() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.endpoint.enabled = false;
        ItemPipeNet net = fixture.pipe.getItemPipeNet();
        assertTrue(net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).isEmpty());
        fixture.endpoint.enabled = true;
        fixture.time(199);
        assertTrue(net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).isEmpty());
        fixture.time(200);
        assertEquals(1, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).size());
    }

    @Test
    void delayedAdditionalEndpointAppearsEvenWhenAnotherDestinationAlreadyWorks() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemPipeTransportFixture.Inventory later = fixture.output(EnumFacing.SOUTH, new ItemStackHandler(1));
        later.enabled = false;
        ItemPipeNet net = fixture.pipe.getItemPipeNet();
        assertEquals(1, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).size());
        later.enabled = true;
        fixture.time(200);
        assertEquals(2, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).size());
    }

    @Test
    void backwardsClockExpiresRouteCache() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.time(100);
        fixture.endpoint.enabled = false;
        ItemPipeNet net = fixture.pipe.getItemPipeNet();
        assertTrue(net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).isEmpty());
        fixture.endpoint.enabled = true;
        fixture.time(0);
        assertEquals(1, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).size());
    }

    @Test
    void disappearingEndpointRejectsWithoutUsingAnAbsentHandler() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).isEmpty());
        fixture.endpoint.enabled = false;
        assertEquals(1, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).getCount());
        assertEquals(1, fixture.destination.getStackInSlot(0).getCount());
    }

    @Test
    void unsidedQueryHasItsOwnCacheButUnsidedInsertionRemainsRejected() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemPipeNet net = fixture.pipe.getItemPipeNet();
        assertEquals(1, net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).size());
        assertEquals(2, net.getNetData(ItemPipeTransportFixture.POS, null).size());
        gregtech.common.pipelike.itempipe.net.ItemNetHandler unsided = new gregtech.common.pipelike.itempipe.net.ItemNetHandler(
                net, fixture.pipe, null);
        assertEquals(1, unsided.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).getCount());
    }

    @Test
    void changingFilterModeImmediatelyRefreshesCapturedPolicy() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        TileEntityItemPipe output = new TileEntityItemPipe();
        fixture.world.put(ItemPipeTransportFixture.POS.east(), output, fixture.block.getDefaultState());
        output.setPipeData(fixture.block, ItemPipeType.NORMAL, gregtech.api.unification.material.Materials.Tin);
        WorldItemPipeNet.getWorldPipeNet(fixture.world).addNode(output.getPos(), output.getNodeData(), 0, 0, false);
        fixture.world.put(output.getPos().east(), new ItemPipeTransportFixture.Inventory(fixture.destination),
                Blocks.CHEST.getDefaultState());
        output.setConnection(EnumFacing.WEST, true, false);
        output.setConnection(EnumFacing.EAST, true, false);
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("transport_regression_filter"),
                (def, holder, side) -> new CoverItemFilter(def, holder, side, "test", null) {

                    @Override
                    public boolean testItemStack(ItemStack stack) {
                        return false;
                    }
                }, ItemStack.EMPTY);
        CoverItemFilter filter = (CoverItemFilter) definition.createCover(fixture.pipe.getCoverableImplementation(),
                EnumFacing.EAST);
        fixture.pipe.getCoverableImplementation().addCover(EnumFacing.EAST, filter);
        filter.setFilterMode(ItemFilterMode.FILTER_INSERT);
        ItemPipeNet net = fixture.pipe.getItemPipeNet();
        ItemStack stack = new ItemStack(Blocks.COBBLESTONE);
        assertTrue(net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).get(0).matchesFilters(stack));
        filter.setFilterMode(ItemFilterMode.FILTER_EXTRACT);
        assertFalse(net.getNetData(ItemPipeTransportFixture.POS, EnumFacing.WEST).get(0).matchesFilters(stack));
    }

    @Test
    void receivingFaceEditInvalidatesSourceRoutesAcrossPaintedComponents() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        TileEntityItemPipe output = new TileEntityItemPipe();
        fixture.world.put(ItemPipeTransportFixture.POS.east(), output, fixture.block.getDefaultState());
        output.setPipeData(fixture.block, ItemPipeType.NORMAL, gregtech.api.unification.material.Materials.Tin);
        WorldItemPipeNet.getWorldPipeNet(fixture.world).addNode(output.getPos(), output.getNodeData(), 0, 0, false);
        fixture.world.put(output.getPos().east(), new ItemPipeTransportFixture.Inventory(fixture.destination),
                Blocks.CHEST.getDefaultState());
        output.setConnection(EnumFacing.WEST, true, false);
        output.setConnection(EnumFacing.EAST, true, false);
        fixture.pipe.setPaintingColor(0xFF0000);
        output.setPaintingColor(0x0000FF);
        assertNotSame(fixture.pipe.getItemPipeNet(), output.getItemPipeNet());
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).isEmpty());
        output.setFaceBlocked(EnumFacing.WEST, true);
        assertEquals(1, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).getCount());
        assertEquals(1, fixture.destination.getStackInSlot(0).getCount());
    }
}

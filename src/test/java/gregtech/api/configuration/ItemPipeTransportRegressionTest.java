package gregtech.api.configuration;

import gregtech.api.util.GTTransferUtils;
import gregtech.common.pipelike.itempipe.net.ItemNetHandler;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipeTickable;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemPipeTransportRegressionTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void slowExactBatchProgressesAndConservesWithBothCoverTickOrders() {
        for (boolean coverFirst : new boolean[] { false, true }) {
            ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
            fixture.source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
            for (int tick = 0; tick <= 40; tick++) {
                fixture.time(tick);
                if (coverFirst) fixture.pipe.update();
                GTTransferUtils.moveInventoryItems(fixture.source, fixture.input);
                if (!coverFirst) fixture.pipe.update();
                assertEquals(64, fixture.source.getStackInSlot(0).getCount() +
                        fixture.destination.getStackInSlot(0).getCount());
            }
            assertTrue(fixture.source.getStackInSlot(0).isEmpty());
            assertEquals(64, fixture.destination.getStackInSlot(0).getCount());
        }
    }

    @Test
    void repeatedProbesDoNotSpendAllowanceArmCreditOrFairness() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        ItemStack batch = new ItemStack(Blocks.COBBLESTONE, 64);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals(64, fixture.input.insertItem(0, batch, true).getCount());
        }
        assertEquals(0, fixture.arm.getBuffer());
        assertEquals(0, fixture.pipe.getTransferredItems());
        assertTrue(fixture.pipe.getTransferred().isEmpty());
        fixture.time(20);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertTrue(fixture.input.insertItem(0, batch, true).isEmpty());
        }
        assertEquals(0, fixture.arm.getBuffer());
        assertEquals(0, fixture.pipe.getTransferredItems());
        assertTrue(fixture.destination.getStackInSlot(0).isEmpty());
        assertTrue(fixture.input.insertItem(0, batch, false).isEmpty());
        assertEquals(64, fixture.destination.getStackInSlot(0).getCount());
        assertEquals(64, fixture.input.insertItem(0, batch, false).getCount());
    }

    @Test
    void exactCreditIsSharedByDifferentFacesAndProducers() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        ItemStackHandler second = new ItemStackHandler(1);
        fixture.source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
        second.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
        fixture.output(EnumFacing.NORTH, second);
        ItemNetHandler north = (ItemNetHandler) fixture.pipe.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        assertNotNull(north);
        assertEquals(64, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), true).getCount(),
                "initial probe");
        fixture.time(20);
        assertEquals(64, fixture.pipe.getAvailableExactTransferItems(32, 64), "elapsed exact allowance");
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), true).isEmpty(),
                "ready direct probe");
        assertEquals(64, fixture.source.extractItem(0, Integer.MAX_VALUE, true).getCount(), "source probe");
        GTTransferUtils.moveInventoryItems(fixture.source, fixture.input);
        assertTrue(fixture.source.getStackInSlot(0).isEmpty(),
                "first producer did not move; sink=" + fixture.destination.getStackInSlot(0).getCount() +
                        ", ledger=" + fixture.pipe.getTransferredItems());
        assertEquals(64, fixture.destination.getStackInSlot(0).getCount(), "first producer sink");
        assertEquals(64, fixture.pipe.getTransferredItems(), "first producer ledger");
        assertEquals(0, fixture.pipe.getAvailableExactTransferItems(32, 64), "spent exact credit");
        assertEquals(64, second.getStackInSlot(0).getCount(), "second producer before its own attempt");
        GTTransferUtils.moveInventoryItems(second, north);
        assertEquals(64, fixture.destination.getStackInSlot(0).getCount());
        assertEquals(64, second.getStackInSlot(0).getCount());
        assertEquals(64, fixture.pipe.getTransferredItems());
    }

    @Test
    void ordinaryTrafficKeepsItsTwentyTickWindow() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemStack batch = new ItemStack(Blocks.COBBLESTONE, 64);
        assertEquals(32, fixture.input.insertItem(0, batch, false).getCount());
        assertEquals(32, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 32), false).getCount());
        fixture.time(19);
        assertEquals(32, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 32), false).getCount());
        fixture.time(20);
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 32), false).isEmpty());
        assertEquals(64, fixture.destination.getStackInSlot(0).getCount());
    }

    @Test
    void firstObservationDoesNotBorrowAlreadySpentCurrentAllowance() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        fixture.pipe.addTransferredItems(32);
        ItemStack batch = new ItemStack(Blocks.COBBLESTONE, 64);
        assertEquals(64, fixture.input.insertItem(0, batch, true).getCount());
        fixture.time(20);
        assertEquals(64, fixture.input.insertItem(0, batch, true).getCount());
        fixture.time(40);
        assertTrue(fixture.input.insertItem(0, batch, false).isEmpty());
        assertEquals(64, fixture.destination.getStackInSlot(0).getCount());
    }

    @Test
    void ordinaryTransfersSpendOnlyTheirActualAmountFromAccumulatedCredit() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), true);
        fixture.time(20);
        assertEquals(64, fixture.pipe.getAvailableExactTransferItems(32, 64));
        fixture.pipe.addTransferredItems(0);
        assertEquals(64, fixture.pipe.getAvailableExactTransferItems(32, 64));
        fixture.pipe.addTransferredItems(1);
        assertEquals(63, fixture.pipe.getAvailableExactTransferItems(32, 64));
    }

    @Test
    void earlierOrdinaryTrafficCannotPerpetuallyShaveAnExactBatch() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        ItemStack batch = new ItemStack(Blocks.COBBLESTONE, 64);
        fixture.input.insertItem(0, batch, true);
        for (int second = 0; second < 5 && fixture.destination.getStackInSlot(0).isEmpty(); second++) {
            fixture.time(second * 20L);
            fixture.pipe.addTransferredItems(1);
            fixture.input.insertItem(0, batch, false);
        }
        assertEquals(64, fixture.destination.getStackInSlot(0).getCount());
    }

    @Test
    void backwardsClockDoesNotKeepFutureExactCredit() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        fixture.time(100);
        ItemStack batch = new ItemStack(Blocks.COBBLESTONE, 64);
        assertEquals(64, fixture.input.insertItem(0, batch, true).getCount());
        fixture.time(120);
        assertTrue(fixture.input.insertItem(0, batch, false).isEmpty());
        fixture.destination.setStackInSlot(0, ItemStack.EMPTY);
        fixture.time(0);
        assertEquals(64, fixture.input.insertItem(0, batch, false).getCount());
        assertTrue(fixture.destination.getStackInSlot(0).isEmpty());
        fixture.time(20);
        assertTrue(fixture.input.insertItem(0, batch, false).isEmpty());
    }

    @Test
    void retainedHandlerRebindsAfterTilePromotionAndRetainsAccounting() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 8), false);
        TileEntityItemPipeTickable replacement = new TileEntityItemPipeTickable();
        fixture.world.put(ItemPipeTransportFixture.POS, replacement, fixture.block.getDefaultState());
        replacement.transferDataFrom(fixture.pipe);
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 8), false).isEmpty());
        assertEquals(16, replacement.getTransferredItems());
        assertEquals(16, fixture.destination.getStackInSlot(0).getCount());
    }

    @Test
    void unloadedSourceRejectsRetainedCapabilityWithoutReadingTheTile() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.world.unloaded.add(ItemPipeTransportFixture.POS);
        assertEquals(8, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 8), false).getCount());
        assertTrue(fixture.destination.getStackInSlot(0).isEmpty());
    }

    @Test
    void closingBareIngressAlsoClosesPreviouslyRetainedCapability() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.pipe.setConnection(EnumFacing.WEST, false, false);
        assertEquals(8, fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 8), false).getCount());
        assertTrue(fixture.destination.getStackInSlot(0).isEmpty());
    }

    @Test
    void nestedCallsCannotSpendTheSameIngressAllowanceAcrossFaces() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemStackHandler northInventory = new ItemStackHandler(1);
        northInventory.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
        fixture.output(EnumFacing.NORTH, northInventory);
        ItemNetHandler north = (ItemNetHandler) fixture.pipe.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        int[] nestedAccepted = { 0 };
        fixture.output(EnumFacing.EAST, new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!simulate) nestedAccepted[0] += 1 - north.insertItem(0,
                        new ItemStack(Blocks.COBBLESTONE), false).getCount();
                return super.insertItem(slot, stack, simulate);
            }
        });
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 8), false).isEmpty());
        assertEquals(0, nestedAccepted[0]);
        assertEquals(8, fixture.pipe.getTransferredItems());
    }

    @Test
    void thrownDestinationDoesNotLeaveIngressGuardLatched() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.output(EnumFacing.EAST, new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                throw new IllegalStateException("regression destination");
            }
        });
        assertThrows(IllegalStateException.class,
                () -> fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false));
        fixture.output(EnumFacing.EAST, fixture.destination);
        assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).isEmpty());
        assertEquals(1, fixture.destination.getStackInSlot(0).getCount());
    }

    @Test
    void largeRuntimeCountSaturatesAndNegativeChargesAreRejected() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.pipe.addTransferredItems(Integer.MAX_VALUE);
        fixture.pipe.addTransferredItems(1);
        assertEquals(Integer.MAX_VALUE, fixture.pipe.getTransferredItems());
        assertThrows(IllegalArgumentException.class, () -> fixture.pipe.addTransferredItems(-1));
        fixture.time(20);
        assertEquals(0, fixture.pipe.getTransferredItems());
    }

    @Test
    void largeVirtualInsertionIsPureAndChargesTheWholeActualAmountOnce() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(4, 0);
        ItemStackHandler destination = new ItemStackHandler(2);
        fixture.output(EnumFacing.EAST, destination);
        ItemStack packet = new ItemStack(Blocks.COBBLESTONE, 128);
        for (int attempt = 0; attempt < 20; attempt++) {
            assertTrue(fixture.input.insertItem(0, packet, true).isEmpty());
        }
        assertEquals(0, fixture.pipe.getTransferredItems());
        assertTrue(destination.getStackInSlot(0).isEmpty());
        assertTrue(destination.getStackInSlot(1).isEmpty());
        assertTrue(fixture.input.insertItem(0, packet, false).isEmpty());
        assertEquals(128, fixture.pipe.getTransferredItems());
        assertEquals(128, destination.getStackInSlot(0).getCount() + destination.getStackInSlot(1).getCount());
    }
}

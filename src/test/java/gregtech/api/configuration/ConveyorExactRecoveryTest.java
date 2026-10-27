package gregtech.api.configuration;

import gregtech.api.capability.impl.ItemHandlerDelegate;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverRoboticArm;
import gregtech.common.covers.TransferMode;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConveyorExactRecoveryTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void sourceExactBatchCanSplitAcrossNetworkDestinations() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemStackHandler first = limited(4);
        ItemStackHandler second = limited(4);
        fixture.output(EnumFacing.EAST, first);
        fixture.output(EnumFacing.SOUTH, second);
        fixture.source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 8));
        assertTrue(new ExactConveyor(fixture.pipe.getCoverableImplementation())
                .transfer(fixture.source, fixture.input, 8));
        assertTrue(fixture.source.getStackInSlot(0).isEmpty());
        assertEquals(4, first.getStackInSlot(0).getCount());
        assertEquals(4, second.getStackInSlot(0).getCount());
    }

    @Test
    void exactSourceRestoresBatchWhenActualTargetRejects() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 8));
        ItemStackHandler target = new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return simulate ? ItemStack.EMPTY : stack;
            }
        };
        assertFalse(new ExactConveyor(fixture.pipe.getCoverableImplementation()).transfer(fixture.source, target, 8));
        assertEquals(8, fixture.source.getStackInSlot(0).getCount());
        assertTrue(target.getStackInSlot(0).isEmpty());
    }

    @Test
    void ordinaryRecoveryDropsRejectedItemsWhenBlockDropsAreDisabled() {
        rejectedItemsReachWorldOwner(false);
    }

    @Test
    void exactRecoveryDropsRejectedItemsWhenBlockDropsAreDisabled() {
        rejectedItemsReachWorldOwner(true);
    }

    private static void rejectedItemsReachWorldOwner(boolean exact) {
        ConveyorRecoveryTestWorld world = new ConveyorRecoveryTestWorld();
        world.getGameRules().setOrCreateGameRule("doTileDrops", "false");
        ItemStackHandler backing = new ItemStackHandler(1);
        backing.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 8));
        IItemHandler source = new ItemHandlerDelegate(backing) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
        ItemStackHandler target = new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return simulate ? ItemStack.EMPTY : stack;
            }
        };
        SourceArmTestView view = new SourceArmTestView(world, source, target);
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("rejected_recovery_probe"),
                (def, holder, side) -> exact ? new CoverRoboticArm(def, holder, side, 1, 32) :
                        new CoverConveyor(def, holder, side, 1, 32),
                ItemStack.EMPTY);
        CoverConveyor conveyor = (CoverConveyor) definition.createCover(view, EnumFacing.EAST);
        if (conveyor instanceof CoverRoboticArm arm) {
            arm.setTransferMode(TransferMode.TRANSFER_EXACT);
            arm.getItemFilterContainer().setTransferSize(8);
        }
        view.cover = conveyor;
        view.tick = 5;

        conveyor.update();

        assertTrue(backing.getStackInSlot(0).isEmpty());
        assertTrue(target.getStackInSlot(0).isEmpty());
        int dropped = 0;
        for (ItemStack stack : world.dropped) {
            assertTrue(ItemStack.areItemsEqual(new ItemStack(Blocks.COBBLESTONE), stack));
            dropped += stack.getCount();
        }
        assertEquals(8, dropped);
        assertEquals(8, backing.getStackInSlot(0).getCount() + target.getStackInSlot(0).getCount() + dropped);
    }

    @Test
    void changedRealExtractionRestoresEarlierEscrowBeforeAbortingBatch() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemStackHandler source = new ItemStackHandler(2) {

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == 1 && !simulate ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
            }
        };
        source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 4));
        source.setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE, 4));
        ItemStackHandler target = new ItemStackHandler(1);
        assertFalse(new ExactConveyor(fixture.pipe.getCoverableImplementation()).transfer(source, target, 8));
        assertEquals(8, source.getStackInSlot(0).getCount() + source.getStackInSlot(1).getCount());
        assertTrue(target.getStackInSlot(0).isEmpty());
    }

    @Test
    void sourceExactBatchLargerThanOneStackCanSplitAcrossNetworkDestinations() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(4, 0);
        ItemStackHandler first = new ItemStackHandler(1);
        ItemStackHandler second = new ItemStackHandler(1);
        fixture.output(EnumFacing.EAST, first);
        fixture.output(EnumFacing.SOUTH, second);
        ItemStackHandler source = new ItemStackHandler(2);
        source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
        source.setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE, 64));
        assertTrue(new ExactConveyor(fixture.pipe.getCoverableImplementation()).transfer(source, fixture.input, 128));
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertTrue(source.getStackInSlot(1).isEmpty());
        assertEquals(128, first.getStackInSlot(0).getCount() + second.getStackInSlot(0).getCount());
        assertEquals(128, fixture.pipe.getTransferredItems());
    }

    @Test
    void slowDestinationExactBatchLargerThanOneStackAccumulatesOnlyElapsedCredit() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(128);
        ItemStackHandler destination = new ItemStackHandler(2);
        fixture.output(EnumFacing.EAST, destination);
        ItemStackHandler source = new ItemStackHandler(2);
        source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
        source.setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE, 64));
        ExactConveyor mover = new ExactConveyor(fixture.pipe.getCoverableImplementation());
        for (int tick = 0; tick < 60; tick++) {
            fixture.time(tick);
            assertFalse(mover.transfer(source, fixture.input, 128));
            assertEquals(128, source.getStackInSlot(0).getCount() + source.getStackInSlot(1).getCount());
        }
        fixture.time(60);
        assertTrue(mover.transfer(source, fixture.input, 128));
        assertEquals(128, destination.getStackInSlot(0).getCount() + destination.getStackInSlot(1).getCount());
        assertEquals(128, fixture.pipe.getTransferredItems());
    }

    @Test
    void ordinaryCoverRestoresAnUnexpectedRealSourceItemInsteadOfBypassingItsFilter() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        ItemStackHandler source = new ItemStackHandler(1) {

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                return simulate && !extracted.isEmpty() ? new ItemStack(Blocks.COBBLESTONE, extracted.getCount()) :
                        extracted;
            }
        };
        source.setStackInSlot(0, new ItemStack(Blocks.DIRT, 8));
        ItemStackHandler target = new ItemStackHandler(1);
        assertEquals(0, new ExactConveyor(fixture.pipe.getCoverableImplementation()).transferAny(source, target, 8));
        assertEquals(8, source.getStackInSlot(0).getCount());
        assertEquals(new ItemStack(Blocks.DIRT).getItem(), source.getStackInSlot(0).getItem());
        assertTrue(target.getStackInSlot(0).isEmpty());
    }

    private static ItemStackHandler limited(int capacity) {
        return new ItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return capacity;
            }
        };
    }

    private static final class ExactConveyor extends CoverConveyor {

        private ExactConveyor(CoverableView holder) {
            super(new CoverDefinition(GTUtility.gregtechId("exact_recovery_probe"),
                    (def, view, side) -> new CoverConveyor(def, view, side, 1, 64), ItemStack.EMPTY),
                    holder, EnumFacing.WEST, 1, 64);
        }

        private boolean transfer(IItemHandler source, IItemHandler target, int count) {
            IntArrayList slots = new IntArrayList();
            for (int slot = 0; slot < source.getSlots(); slot++) slots.add(slot);
            return moveInventoryItemsExact(source, target,
                    new TypeItemInfo(new ItemStack(Blocks.COBBLESTONE), -1, slots, count));
        }

        private int transferAny(IItemHandler source, IItemHandler target, int count) {
            return moveInventoryItems(source, target, count);
        }
    }
}

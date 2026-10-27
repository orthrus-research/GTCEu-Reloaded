package gregtech.api.configuration;

import gregtech.api.capability.impl.ItemHandlerDelegate;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.ItemTransferException;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ItemTransferRecoveryTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void realRejectionRestoresOrdinarySource() {
        ItemStackHandler source = source(8);
        ItemStackHandler target = rejectingTarget();
        assertTrue(GTTransferUtils.moveInventoryItemsWithRemainders(source, target).isEmpty());
        assertEquals(8, source.getStackInSlot(0).getCount());
        assertTrue(target.getStackInSlot(0).isEmpty());
    }

    @Test
    void partialRealAcceptanceRemovesOnlyActualDestinationCount() {
        ItemStackHandler source = source(8);
        ItemStackHandler target = new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) return ItemStack.EMPTY;
                ItemStack offered = stack.copy();
                offered.setCount(Math.min(3, stack.getCount()));
                ItemStack rejected = super.insertItem(slot, offered, false);
                ItemStack remainder = stack.copy();
                remainder.shrink(offered.getCount() - rejected.getCount());
                return remainder;
            }
        };
        assertTrue(GTTransferUtils.moveInventoryItemsWithRemainders(source, target).isEmpty());
        assertEquals(5, source.getStackInSlot(0).getCount());
        assertEquals(3, target.getStackInSlot(0).getCount());
    }

    @Test
    void modifiableExtractionOnlyInventoryCanRestoreItsOwnExtractedItems() {
        ItemStackHandler source = new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
        source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 8));
        assertTrue(GTTransferUtils.moveInventoryItemsWithRemainders(source, rejectingTarget()).isEmpty());
        assertEquals(8, source.getStackInSlot(0).getCount());
    }

    @Test
    void unrestorableItemsReturnToTheOwnerInsteadOfDisappearing() {
        ItemStackHandler backing = source(8);
        IItemHandler source = extractionOnly(backing);
        List<ItemStack> leftovers = GTTransferUtils.moveInventoryItemsWithRemainders(source, rejectingTarget());
        assertTrue(backing.getStackInSlot(0).isEmpty());
        assertEquals(1, leftovers.size());
        assertEquals(8, leftovers.get(0).getCount());
    }

    @Test
    void legacyVoidApiCarriesUnrestorableItemsInAnExplicitFailure() {
        IItemHandler source = extractionOnly(source(8));
        ItemTransferException failure = assertThrows(ItemTransferException.class,
                () -> GTTransferUtils.moveInventoryItems(source, rejectingTarget()));
        assertEquals(8, failure.getRemainders().get(0).getCount());
        failure.getRemainders().get(0).setCount(1);
        assertEquals(8, failure.getRemainders().get(0).getCount());
    }

    @Test
    void changedSourceItemIsRestoredWithoutSendingTheUnexpectedType() {
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
        assertTrue(GTTransferUtils.moveInventoryItemsWithRemainders(source, target).isEmpty());
        assertEquals(new ItemStack(Blocks.DIRT).getItem(), source.getStackInSlot(0).getItem());
        assertEquals(8, source.getStackInSlot(0).getCount());
        assertTrue(target.getStackInSlot(0).isEmpty());
    }

    private static ItemStackHandler source(int count) {
        ItemStackHandler source = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, count));
        return source;
    }

    private static ItemStackHandler rejectingTarget() {
        return new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return simulate ? ItemStack.EMPTY : stack;
            }
        };
    }

    private static IItemHandler extractionOnly(ItemStackHandler backing) {
        return new ItemHandlerDelegate(backing) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
    }
}

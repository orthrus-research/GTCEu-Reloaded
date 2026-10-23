package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferStorageTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void acceptsOwnedLargeCountAndTaggedFluidWithoutExposingAliases() {
        AtomicInteger changed = new AtomicInteger();
        PatternBufferStorage storage = storage(changed::incrementAndGet);
        ItemStack item = new ItemStack(Items.IRON_INGOT, 513);
        ItemStack fluid = drop(1347, "first");
        InventoryCrafting table = table(item, fluid);
        int before = changed.get();
        assertTrue(storage.accept(0, details(item, fluid), table, true));
        assertEquals(before + 1, changed.get(), "Staging must not notify before the atomic acceptance");
        assertTrue(table.isEmpty());
        PatternBufferBatch batch = storage.getBatches().get(0);
        ItemStack leakedItem = batch.getItems().getStackInSlot(0);
        leakedItem.setCount(1);
        FluidStack leakedFluid = batch.getFluids().getTankAt(0).getFluid();
        leakedFluid.amount = 1;
        leakedFluid.tag.setString("batch", "mutated");
        assertEquals(513, batch.getItems().getStackInSlot(0).getCount());
        assertEquals(1347, batch.getFluids().getTankAt(0).getFluidAmount());
        assertEquals("first", batch.getFluids().getTankAt(0).getFluid().tag.getString("batch"));
        assertEquals(300, batch.getItems().extractItem(0, 300, true).getCount());
        assertEquals(513, batch.getItems().getStackInSlot(0).getCount());
        assertEquals(300, batch.getItems().extractItem(0, 300, false).getCount());
        assertEquals(213, batch.getItems().getStackInSlot(0).getCount());
        assertEquals(50, batch.getFluids().drain(50, true).amount);
    }

    @Test
    void mismatchOverflowAndOversizedInputsLeaveTableAndLedgerUnchanged() {
        AtomicInteger notifications = new AtomicInteger();
        PatternBufferStorage storage = storage(notifications::incrementAndGet);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 2);
        InventoryCrafting table = table(iron);
        NBTTagCompound before = storage.write();
        int changes = notifications.get();
        assertFalse(storage.accept(0, details(new ItemStack(Items.IRON_INGOT)), table, false));
        assertEquals(before, storage.write());
        assertEquals(changes, notifications.get());
        assertEquals(2, table.getStackInSlot(0).getCount());

        ItemStack huge = new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE);
        InventoryCrafting overflowing = table(huge, huge);
        assertFalse(storage.accept(0, details(huge, huge), overflowing, false));
        assertEquals(Integer.MAX_VALUE, overflowing.getStackInSlot(1).getCount());
        IAEItemStack excessive = AEItemStack.fromItemStack(iron).setStackSize((long) Integer.MAX_VALUE + 1);
        assertFalse(storage.accept(0, details(new IAEItemStack[] { excessive }), table, false));

        ItemStack tagged = new ItemStack(Items.IRON_INGOT);
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound().setByteArray("oversized", new byte[PatternBufferCodec.MAX_BATCH_BYTES]);
        InventoryCrafting largeTable = table(tagged);
        assertFalse(storage.accept(0, details(tagged), largeTable, false));
        assertEquals(before, storage.write());
        assertEquals(changes, notifications.get());
        assertTrue(ItemStack.areItemStacksEqual(tagged, largeTable.getStackInSlot(0)));
    }

    @Test
    void batchesStaySeparateAndFullQueueNeverConsumesNextHandoff() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        for (int i = 0; i < 27; i++) assertTrue(storage.accept(0, details(iron), table(iron), false));
        assertEquals(27, storage.getBatches().size());
        assertNotEquals(storage.getBatches().get(0).getId(), storage.getBatches().get(1).getId());
        InventoryCrafting last = table(iron);
        NBTTagCompound before = storage.write();
        assertFalse(storage.canAccept());
        assertFalse(storage.accept(0, details(iron), last, false));
        assertEquals(before, storage.write());
        assertFalse(last.isEmpty());
    }

    @Test
    void ownedPatternIsLockedAndEmptyBatchOnlyPrunesAfterExplicitAcknowledgement() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        assertTrue(storage.accept(0, details(iron), table(iron), false));
        assertTrue(storage.getPatterns().extractItem(0, 1, false).isEmpty());
        storage.getPatterns().setStackInSlot(0, ItemStack.EMPTY);
        assertFalse(storage.getPatterns().getStackInSlot(0).isEmpty());
        assertEquals(1, storage.getPatterns().insertItem(1, pattern(), true).getCount(), "Duplicate patterns rejected");
        PatternBufferBatch batch = storage.getBatches().get(0);
        assertEquals(1, batch.getItems().insertItem(0, iron, false).getCount());
        batch.getItems().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        assertEquals(Items.IRON_INGOT, batch.getItems().getStackInSlot(0).getItem());
        batch.getItems().extractItem(0, 1, false);
        assertEquals(1, storage.getBatches().size());
        storage.pruneEmpty();
        assertEquals(0, storage.getBatches().size());
        assertEquals(1, storage.getPatterns().extractItem(0, 1, false).getCount());
    }

    @Test
    void partialRefundRetainsExactRemaindersAndNeverRefundsConsumedInputs() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 200);
        ItemStack water = drop(1000, "refund");
        assertTrue(storage.accept(0, details(iron, water), table(iron, water), true));
        PatternBufferBatch batch = storage.getBatches().get(0);
        batch.getItems().extractItem(0, 30, false);
        batch.getFluids().drain(100, true);
        storage.requestRefund(batch.getId());
        assertTrue(batch.getItems().extractItem(0, 10, false).isEmpty());
        assertNull(batch.getFluids().drain(10, true));
        AtomicInteger items = new AtomicInteger(), fluids = new AtomicInteger(), attempts = new AtomicInteger();
        PatternBufferRefund partial = new PatternBufferRefund() {

            @Override
            public int insertItem(ItemStack stack) {
                attempts.incrementAndGet();
                items.addAndGet(17);
                return 17;
            }

            @Override
            public int insertFluid(FluidStack fluid) {
                attempts.incrementAndGet();
                fluids.addAndGet(90);
                return 90;
            }
        };
        storage.refund(partial);
        assertEquals(2, attempts.get(), "One request per remaining entry per pass");
        assertEquals(153, batch.getItems().getStackInSlot(0).getCount());
        assertEquals(810, batch.getFluids().getTankAt(0).getFluidAmount());
        PatternBufferStorage reload = storage(() -> {});
        reload.read(storage.write());
        assertTrue(reload.getBatches().get(0).isRefundPending());
        reload.refund(new PatternBufferRefund() {

            @Override
            public int insertItem(ItemStack stack) {
                items.addAndGet(stack.getCount());
                return stack.getCount();
            }

            @Override
            public int insertFluid(FluidStack fluid) {
                assertEquals("refund", fluid.tag.getString("batch"));
                fluids.addAndGet(fluid.amount);
                return fluid.amount;
            }
        });
        assertEquals(170, items.get());
        assertEquals(900, fluids.get());
        assertTrue(reload.getBatches().isEmpty());
    }

    @Test
    void offlineRefundAndUnrequestedBatchLeaveAllMaterialsIntact() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 6);
        assertTrue(storage.accept(0, details(iron), table(iron), false));
        PatternBufferRefund unavailable = new PatternBufferRefund() {

            @Override
            public int insertItem(ItemStack stack) {
                return 0;
            }

            @Override
            public int insertFluid(FluidStack fluid) {
                return 0;
            }
        };
        NBTTagCompound before = storage.write();
        storage.refund(unavailable);
        assertEquals(before, storage.write());
        storage.requestRefund(0);
        before = storage.write();
        storage.refund(unavailable);
        assertEquals(before, storage.write());
    }

    @Test
    void codecRoundTripPreservesLargeAmountsTagsIdentityAndSharedStores() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE);
        ItemStack water = drop(1347, "saved");
        assertTrue(storage.accept(0, details(iron, water), table(iron, water), true));
        storage.getSharedItems().insertItem(0, new ItemStack(Items.FEATHER, 12), false);
        storage.getSharedFluids().fill(PatternBufferTokens.fluid(drop(321, "shared"), true), true);
        NBTTagCompound saved = storage.write();
        PatternBufferStorage reload = storage(() -> {});
        reload.read(saved);
        assertFalse(reload.isQuarantined());
        assertEquals(saved, reload.write());
        assertEquals(Integer.MAX_VALUE, reload.getBatches().get(0).getItems().getStackInSlot(0).getCount());
        assertEquals(storage.getIdentity(), reload.getIdentity());
        assertEquals(storage.getIncarnation(), reload.getIncarnation());
        reload.renewIncarnation();
        assertEquals(storage.getIdentity(), reload.getIdentity());
        assertNotEquals(storage.getIncarnation(), reload.getIncarnation());
    }

    @Test
    void malformedKnownAndUnknownSchemasQuarantineExactPayloadWithoutPartialExposure() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        assertTrue(storage.accept(0, details(iron), table(iron), false));
        NBTTagCompound bad = storage.write();
        NBTTagList batches = bad.getTagList("Batches", 10);
        batches.getCompoundTagAt(0).setLong("Id", -1);
        storage.read(bad);
        assertTrue(storage.isQuarantined());
        assertFalse(storage.canAccept());
        assertTrue(storage.getBatches().isEmpty());
        assertTrue(storage.getPatterns().getStackInSlot(0).isEmpty());
        assertEquals(bad, storage.write());
        assertEquals(1, storage.getSharedItems().insertItem(0, iron, false).getCount());
        storage.requestRefund(0);
        assertEquals(bad, storage.write());

        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("Version", 84);
        future.setString("UnknownMaterial", "must survive");
        storage.read(future);
        assertTrue(storage.isQuarantined());
        assertEquals(future, storage.write());
        assertTrue(storage.isPortable());
    }

    @Test
    void oversizedQuarantineIsLosslessButExplicitlyNotPortable() {
        PatternBufferStorage storage = storage(() -> {});
        NBTTagCompound large = new NBTTagCompound();
        large.setInteger("Version", 84);
        large.setByteArray("UnknownOwnedPayload", new byte[PatternBufferCodec.MAX_PORTABLE_BYTES + 1]);
        storage.read(large);
        assertTrue(storage.isQuarantined());
        assertFalse(storage.isPortable());
        assertEquals(large, storage.write());
        NBTTagCompound copy = storage.write();
        copy.removeTag("UnknownOwnedPayload");
        assertEquals(large, storage.write());
    }

    @Test
    void arbitraryTableClearOverrideIsRejectedBeforeEitherOwnerChanges() {
        PatternBufferStorage storage = storage(() -> {});
        AtomicInteger clears = new AtomicInteger();
        InventoryCrafting unsafe = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 3, 3) {

            @Override
            public void clear() {
                clears.incrementAndGet();
                throw new IllegalStateException("Must not call");
            }
        };
        unsafe.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT));
        NBTTagCompound before = storage.write();
        assertFalse(storage.accept(0, details(new ItemStack(Items.IRON_INGOT)), unsafe, false));
        assertEquals(0, clears.get());
        assertEquals(before, storage.write());
        assertEquals(1, unsafe.getStackInSlot(0).getCount());
    }

    @Test
    void sequenceExhaustionRejectsWithoutOverflowOrTakingTheCpuTable() {
        PatternBufferStorage storage = storage(() -> {});
        NBTTagCompound exhausted = storage.write();
        exhausted.setLong("Sequence", Long.MAX_VALUE);
        storage.read(exhausted);
        assertFalse(storage.isQuarantined());
        assertFalse(storage.canAccept());
        InventoryCrafting table = table(new ItemStack(Items.IRON_INGOT));
        assertFalse(storage.accept(0, details(new ItemStack(Items.IRON_INGOT)), table, false));
        assertEquals(exhausted, storage.write());
        assertEquals(1, table.getStackInSlot(0).getCount());
    }
}

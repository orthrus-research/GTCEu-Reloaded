package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.capability.impl.FluidTankList;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferCodec.*;

/** Sole physical owner. Decoding is all-or-nothing, and unknown data remains inactive and lossless. */
public final class PatternBufferStorage {

    // Reserve a fixed budget for 27 pattern + 9 shared item + 9 shared fluid identities and the portable envelope.
    private static final int PRIVATE_BYTES = MAX_PORTABLE_BYTES - 384 * 1024;
    private final Runnable changed;
    private final List<PatternBufferBatch> batches = new ArrayList<>();
    private final PatternBufferItems patterns;
    private final PatternBufferItems sharedItems;
    private final PatternBufferTank[] sharedTanks = new PatternBufferTank[9];
    private final FluidTankList sharedFluids;
    private UUID identity = UUID.randomUUID();
    private UUID incarnation = UUID.randomUUID();
    private long sequence;
    private long revision;
    private long patternRevision;
    private NBTTagCompound quarantine;
    private boolean loading;
    private Boolean portable;

    public PatternBufferStorage(Runnable changed, Predicate<ItemStack> patternValidator) {
        this.changed = changed;
        this.patterns = new PatternBufferItems(MAX_PATTERNS, 1, false, () -> {
            this.patternRevision++;
            changed();
        },
                (slot, stack) -> !isQuarantined() && !ownsPattern(slot) &&
                        (stack.isEmpty() || (patternValidator.test(stack) && !duplicatePattern(slot, stack))));
        this.sharedItems = new PatternBufferItems(9, 64, false, this::changed,
                (slot, stack) -> !isQuarantined());
        for (int i = 0; i < sharedTanks.length; i++)
            this.sharedTanks[i] = new PatternBufferTank(64_000, false, this::changed, () -> !isQuarantined());
        this.sharedFluids = new FluidTankList(false, sharedTanks);
    }

    public PatternBufferItems getPatterns() {
        return patterns;
    }

    public PatternBufferItems getSharedItems() {
        return sharedItems;
    }

    public FluidTankList getSharedFluids() {
        return sharedFluids;
    }

    public UUID getIdentity() {
        return identity;
    }

    public UUID getIncarnation() {
        return incarnation;
    }

    public long getRevision() {
        return revision;
    }

    public long getPatternRevision() {
        return patternRevision;
    }

    public List<PatternBufferBatch> getBatches() {
        return Collections.unmodifiableList(batches);
    }

    public boolean isQuarantined() {
        return quarantine != null;
    }

    public boolean canAccept() {
        return !isQuarantined() && batches.size() < MAX_BATCHES && sequence < Long.MAX_VALUE;
    }

    public boolean isPortable() {
        if (portable == null) this.portable = fits(write(), MAX_PORTABLE_BYTES - 4096);
        return portable;
    }

    public void renewIncarnation() {
        this.incarnation = UUID.randomUUID();
        changed();
    }

    public void changed() {
        this.portable = null;
        if (!loading) {
            this.revision++;
            changed.run();
        }
    }

    public boolean ownsPattern(int slot) {
        for (PatternBufferBatch batch : batches) if (batch.getPattern() == slot) return true;
        return false;
    }

    private boolean duplicatePattern(int slot, ItemStack stack) {
        for (int i = 0; i < patterns.getSlots(); i++)
            if (i != slot && ItemStack.areItemStacksEqual(patterns.getStackInSlot(i), stack)) return true;
        return false;
    }

    /** Final mutation follows complete owned staging; rejected tables and stores are unchanged. */
    public boolean accept(int patternSlot, ICraftingPatternDetails pattern, InventoryCrafting table, boolean bridge) {
        if (!canAccept() || patternSlot < 0 || patternSlot >= MAX_PATTERNS || pattern == null) return false;
        ItemStack patternSnapshot = patterns.getStackInSlot(patternSlot);
        if (patternSnapshot.isEmpty() || !ItemStack.areItemStacksEqual(patternSnapshot, pattern.getPattern()))
            return false;
        final PatternBufferStaging staging;
        final PatternBufferBatch batch;
        long observedRevision = revision;
        try {
            staging = PatternBufferStaging.table(pattern, table, bridge);
            batch = new PatternBufferBatch(sequence + 1, patternSlot, staging.items, staging.fluids, this::changed);
            require(!batch.isEmpty() && fits(batch.write(), MAX_BATCH_BYTES), "Invalid or oversized batch");
            NBTTagCompound privateState = new NBTTagCompound();
            NBTTagList list = writeBatches();
            list.appendTag(batch.write());
            privateState.setTag("Batches", list);
            require(fits(privateState, PRIVATE_BYTES), "Pattern buffer payload full");
        } catch (RuntimeException ignored) {
            return false;
        }
        if (!canAccept() || observedRevision != revision || !staging.unchanged(table) ||
                !ItemStack.areItemStacksEqual(patternSnapshot, patterns.getStackInSlot(patternSlot)))
            return false;
        this.batches.add(batch);
        this.sequence++;
        // Legacy CPU relinquishes this table only on true; no externally rejecting handler is involved here.
        table.clear();
        changed();
        return true;
    }

    public void requestRefund(long batchId) {
        if (isQuarantined()) return;
        for (PatternBufferBatch batch : batches) {
            if ((batchId == 0 || batchId == batch.getId()) && !batch.isRefundPending()) {
                batch.setRefundPending(true);
                changed();
            }
        }
    }

    public void refund(PatternBufferRefund sink) {
        if (isQuarantined()) return;
        for (PatternBufferBatch batch : batches) batch.refund(sink);
        pruneEmpty();
    }

    public void pruneEmpty() {
        if (batches.removeIf(PatternBufferBatch::isEmpty)) changed();
    }

    public NBTTagCompound write() {
        if (quarantine != null) return quarantine.copy();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", 1);
        tag.setUniqueId("Identity", identity);
        tag.setUniqueId("Incarnation", incarnation);
        tag.setLong("Sequence", sequence);
        tag.setTag("Patterns", writeItems(patterns));
        tag.setTag("SharedItems", writeItems(sharedItems));
        NBTTagList fluids = new NBTTagList();
        for (PatternBufferTank tank : sharedTanks) {
            FluidStack fluid = tank.getFluid();
            fluids.appendTag(fluid == null ? new NBTTagCompound() : fluid.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("SharedFluids", fluids);
        tag.setTag("Batches", writeBatches());
        return tag;
    }

    private static NBTTagList writeItems(PatternBufferItems items) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < items.getSlots(); i++) list.appendTag(writeItem(items.getStackInSlot(i)));
        return list;
    }

    private NBTTagList writeBatches() {
        NBTTagList list = new NBTTagList();
        for (PatternBufferBatch batch : batches) list.appendTag(batch.write());
        return list;
    }

    public void read(NBTTagCompound source) {
        this.loading = true;
        try {
            this.batches.clear();
            this.quarantine = null;
            for (int i = 0; i < patterns.getSlots(); i++) patterns.restore(i, ItemStack.EMPTY);
            for (int i = 0; i < sharedItems.getSlots(); i++) sharedItems.restore(i, ItemStack.EMPTY);
            for (PatternBufferTank tank : sharedTanks) tank.restore(null);
            require(fits(source, MAX_PORTABLE_BYTES - 4096) && source.hasKey("Version", 3) &&
                    source.getInteger("Version") == 1 && source.hasUniqueId("Identity") &&
                    source.hasUniqueId("Incarnation") && source.hasKey("Sequence", 4) &&
                    source.getLong("Sequence") >= 0, "Unknown or invalid buffer schema");
            List<ItemStack> decodedPatterns = readItems(source, "Patterns", MAX_PATTERNS, 1, MAX_STACK_BYTES);
            List<ItemStack> decodedShared = readItems(source, "SharedItems", 9, 64, MAX_STACK_BYTES);
            require(decodedPatterns.size() == MAX_PATTERNS && decodedShared.size() == 9, "Invalid shared slot count");
            List<FluidStack> decodedFluids = readFluids(source, "SharedFluids", 9, 64_000, MAX_STACK_BYTES);
            require(decodedFluids.size() == 9, "Invalid shared tank count");
            NBTTagList rawBatches = list(source, "Batches", MAX_BATCHES);
            NBTTagCompound privateState = new NBTTagCompound();
            privateState.setTag("Batches", rawBatches);
            require(fits(privateState, PRIVATE_BYTES), "Oversized private payload");
            List<PatternBufferBatch> decodedBatches = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < rawBatches.tagCount(); i++) {
                NBTTagCompound tag = rawBatches.getCompoundTagAt(i);
                require(fits(tag, MAX_BATCH_BYTES) && tag.hasKey("Id", 4) && tag.hasKey("Pattern", 3) &&
                        tag.hasKey("Refund", 1), "Invalid batch fields");
                long id = tag.getLong("Id");
                int slot = tag.getInteger("Pattern");
                require(id > 0 && id <= source.getLong("Sequence") && ids.add(id) &&
                        slot >= 0 && slot < MAX_PATTERNS && !decodedPatterns.get(slot).isEmpty(),
                        "Invalid batch owner");
                PatternBufferBatch batch = new PatternBufferBatch(id, slot,
                        readItems(tag, "Items", MAX_ENTRIES, Integer.MAX_VALUE, MAX_BATCH_BYTES),
                        readFluids(tag, "Fluids", MAX_ENTRIES, Integer.MAX_VALUE, MAX_BATCH_BYTES), this::changed);
                batch.setRefundPending(tag.getBoolean("Refund"));
                decodedBatches.add(batch);
            }
            this.identity = source.getUniqueId("Identity");
            this.incarnation = source.getUniqueId("Incarnation");
            this.sequence = source.getLong("Sequence");
            for (int i = 0; i < patterns.getSlots(); i++) patterns.restore(i, decodedPatterns.get(i));
            for (int i = 0; i < sharedItems.getSlots(); i++) sharedItems.restore(i, decodedShared.get(i));
            for (int i = 0; i < sharedTanks.length; i++) sharedTanks[i].restore(decodedFluids.get(i));
            this.batches.addAll(decodedBatches);
        } catch (RuntimeException ignored) {
            this.batches.clear();
            this.quarantine = source.copy();
        } finally {
            this.loading = false;
            changed();
        }
    }

    private static List<ItemStack> readItems(NBTTagCompound tag, String key, int limit, int amountLimit,
                                             int byteLimit) {
        NBTTagList list = list(tag, key, limit);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            require(fits(entry, byteLimit), "Oversized item entry");
            ItemStack stack = readItem(entry);
            require(stack.isEmpty() || stack.getCount() <= amountLimit, "Oversized item amount");
            items.add(stack);
        }
        return items;
    }

    private static List<FluidStack> readFluids(NBTTagCompound tag, String key, int limit, int amountLimit,
                                               int byteLimit) {
        NBTTagList list = list(tag, key, limit);
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            require(fits(entry, byteLimit), "Oversized fluid entry");
            FluidStack fluid = readFluid(entry);
            require(fluid == null || fluid.amount <= amountLimit, "Oversized fluid amount");
            fluids.add(fluid);
        }
        return fluids;
    }
}

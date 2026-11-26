package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAESlot;

import net.minecraft.network.PacketBuffer;

import appeng.api.storage.data.IAEStack;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * An ME input part's sixteen config slots on its screen: the client is sent what each slot is configured with and
 * holds, and sends back the edits, which the server checks before applying. Stocking parts cannot set amounts, and
 * while auto-pull manages the configs nothing can edit them.
 */
final class MEConfigSyncHandler<T extends IAEStack<T>, N> extends SyncHandler {

    static final int UPDATE_SLOTS = 0;
    static final int SET_FROM_CURSOR = 1;
    static final int SET_FROM_INGREDIENT = 2;
    static final int CLEAR = 3;
    static final int SET_AMOUNT = 4;
    private final MEStackType<T, N> stackType;
    private final ExportOnlyAESlot<T>[] slots;
    private final boolean stocking;
    private final BooleanSupplier autoPull;
    private final Predicate<N> configured;
    private final Runnable edited;
    private final List<T> sentConfigs = new ArrayList<>();
    private final List<T> sentStocks = new ArrayList<>();
    private final List<T> shownConfigs = new ArrayList<>();
    private final List<T> shownStocks = new ArrayList<>();
    private final List<Integer> changed = new ArrayList<>();
    private boolean sentAutoPull;
    private boolean shownAutoPull;
    private int selected = -1;

    /**
     * @param configured whether a stocking part may not take a stack, since one of the multiblock's stocking parts is
     *                   configured with it already
     * @param edited     runs on the server after an edit, to save it
     */
    MEConfigSyncHandler(MEStackType<T, N> stackType, ExportOnlyAESlot<T>[] slots, boolean stocking,
                        BooleanSupplier autoPull, Predicate<N> configured, Runnable edited) {
        this.stackType = stackType;
        this.slots = slots;
        this.stocking = stocking;
        this.autoPull = autoPull;
        this.configured = configured;
        this.edited = edited;
        for (int i = 0; i < slots.length; i++) {
            this.sentConfigs.add(null);
            this.sentStocks.add(null);
            this.shownConfigs.add(null);
            this.shownStocks.add(null);
        }
    }

    MEStackType<T, N> getStackType() {
        return stackType;
    }

    int getSize() {
        return slots.length;
    }

    boolean isStocking() {
        return stocking;
    }

    /** Whether auto-pull manages the configs, as the client was last told. */
    boolean isAutoPull() {
        return shownAutoPull;
    }

    @Nullable
    T getConfig(int index) {
        return shownConfigs.get(index);
    }

    @Nullable
    T getStock(int index) {
        return shownStocks.get(index);
    }

    /** The slot whose amount the screen edits, or -1; only the client's screen has one. */
    int getSelected() {
        return selected;
    }

    void select(int index) {
        this.selected = stocking ? -1 : index;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (collectChanges(init)) syncToClient(UPDATE_SLOTS, this::writeChanges);
    }

    /**
     * Notes which slots changed since the client was last told, and the auto-pull state.
     *
     * @return whether there is an update to send
     */
    boolean collectChanges(boolean init) {
        boolean autoPullNow = autoPull.getAsBoolean();
        changed.clear();
        for (int i = 0; i < slots.length; i++) {
            T config = slots[i].getConfig();
            T stock = slots[i].getStock();
            if (init || !sameAmount(config, sentConfigs.get(i)) || !sameAmount(stock, sentStocks.get(i))) {
                changed.add(i);
                sentConfigs.set(i, config == null ? null : config.copy());
                sentStocks.set(i, stock == null ? null : stock.copy());
            }
        }
        if (!init && changed.isEmpty() && autoPullNow == sentAutoPull) return false;
        this.sentAutoPull = autoPullNow;
        return true;
    }

    /** Writes the update {@link #collectChanges} noted, which {@link #readOnClient} reads. */
    void writeChanges(PacketBuffer buf) {
        buf.writeBoolean(sentAutoPull);
        buf.writeVarInt(changed.size());
        for (int index : changed) {
            buf.writeVarInt(index);
            writeShown(buf, sentConfigs.get(index));
            writeShown(buf, sentStocks.get(index));
        }
    }

    private static <S extends IAEStack<S>> boolean sameAmount(@Nullable S a, @Nullable S b) {
        if (a == b) return true;
        return a != null && b != null && a.getStackSize() == b.getStackSize() && a.equals(b);
    }

    private void writeShown(PacketBuffer buf, @Nullable T stack) {
        buf.writeBoolean(stack != null);
        if (stack != null) stackType.writeShown(buf, stack);
    }

    @Nullable
    private T readShown(PacketBuffer buf) {
        return buf.readBoolean() ? stackType.readShown(buf) : null;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id != UPDATE_SLOTS) return;
        this.shownAutoPull = buf.readBoolean();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            int index = buf.readVarInt();
            T config = readShown(buf);
            T stock = readShown(buf);
            if (index < 0 || index >= slots.length) continue;
            shownConfigs.set(index, config);
            shownStocks.set(index, stock);
        }
        if (shownAutoPull) this.selected = -1;
    }

    /** Asks the server to configure a slot with what the cursor holds. */
    void setFromCursor(int index) {
        syncToServer(SET_FROM_CURSOR, buf -> buf.writeVarInt(index));
    }

    void setFromIngredient(int index, N stack) {
        syncToServer(SET_FROM_INGREDIENT, buf -> {
            buf.writeVarInt(index);
            stackType.writeNative(buf, stack);
        });
    }

    void clear(int index) {
        if (selected == index) this.selected = -1;
        syncToServer(CLEAR, buf -> buf.writeVarInt(index));
    }

    void setAmount(int index, long amount) {
        syncToServer(SET_AMOUNT, buf -> {
            buf.writeVarInt(index);
            buf.writeVarLong(amount);
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        int index = buf.readVarInt();
        switch (id) {
            case SET_FROM_CURSOR -> configure(index, stackType.fromCursor(getSyncManager().getCursorItem()));
            case SET_FROM_INGREDIENT -> configure(index, stackType.readNative(buf));
            case CLEAR -> {
                if (isEditable(index)) {
                    slots[index].setConfig(null);
                    edited.run();
                }
            }
            case SET_AMOUNT -> {
                long amount = buf.readVarLong();
                // amounts mean nothing to a stocking part; an amount must be positive and fit an int
                if (!stocking && isEditable(index) && amount > 0 && slots[index].getConfig() != null) {
                    slots[index].getConfig().setStackSize(Math.min(amount, Integer.MAX_VALUE));
                    edited.run();
                }
            }
            default -> {}
        }
    }

    private boolean isEditable(int index) {
        return index >= 0 && index < slots.length && !autoPull.getAsBoolean();
    }

    private void configure(int index, @Nullable N stack) {
        if (stack == null || !isEditable(index)) return;
        // a stocking part takes nothing another stocking slot of the multiblock already shows
        if (stocking && configured.test(stack)) return;
        slots[index].setConfig(stackType.wrap(stack));
        edited.run();
    }
}

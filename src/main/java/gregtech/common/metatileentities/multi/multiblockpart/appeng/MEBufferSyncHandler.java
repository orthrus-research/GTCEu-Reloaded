package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import net.minecraft.network.PacketBuffer;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What an ME output part's buffer holds, sent to its screen whenever an entry or an amount changes. */
final class MEBufferSyncHandler<T extends IAEStack<T>> extends SyncHandler {

    static final int UPDATE_ENTRIES = 0;
    private final MEStackType<T, ?> stackType;
    private final IItemList<T> buffer;
    private List<T> sent = Collections.emptyList();
    private List<T> shown = Collections.emptyList();

    MEBufferSyncHandler(MEStackType<T, ?> stackType, IItemList<T> buffer) {
        this.stackType = stackType;
        this.buffer = buffer;
    }

    /** The entries, in the buffer's order, as the client was last sent them. */
    List<T> getEntries() {
        return shown;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (collectChanges(init)) syncToClient(UPDATE_ENTRIES, this::writeEntries);
    }

    /** @return whether the buffer changed since the client was last sent it */
    boolean collectChanges(boolean init) {
        List<T> current = new ArrayList<>();
        for (T stack : buffer) {
            if (stack.getStackSize() > 0) current.add(stack.copy());
        }
        if (!init && sameEntries(current, sent)) return false;
        this.sent = current;
        return true;
    }

    /** Writes the entries {@link #collectChanges} took, which {@link #readOnClient} reads. */
    void writeEntries(PacketBuffer buf) {
        buf.writeVarInt(sent.size());
        for (T stack : sent) stackType.writeShown(buf, stack);
    }

    private static <S extends IAEStack<S>> boolean sameEntries(List<S> a, List<S> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getStackSize() != b.get(i).getStackSize() || !a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id != UPDATE_ENTRIES) return;
        int count = buf.readVarInt();
        List<T> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            T stack = stackType.readShown(buf);
            if (stack != null) entries.add(stack);
        }
        this.shown = entries;
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}
}

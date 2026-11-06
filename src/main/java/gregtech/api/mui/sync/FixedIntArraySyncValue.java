package gregtech.api.mui.sync;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sync Value for an array with fixed length.
 * <p>
 * Reuses a fixed-size cache, isolated from the source array.
 */
public class FixedIntArraySyncValue extends ValueSyncHandler<int[]> {

    private final int[] cache;
    private final Supplier<int[]> getter;
    private final @Nullable Consumer<int[]> setter;

    public FixedIntArraySyncValue(@NotNull Supplier<int[]> getter, @Nullable Consumer<int[]> setter) {
        this.getter = Objects.requireNonNull(getter);
        this.setter = setter;
        this.cache = getter.get().clone();
    }

    @Contract("null, _, null, _ -> fail")
    public FixedIntArraySyncValue(@Nullable Supplier<int[]> clientGetter, @Nullable Consumer<int[]> clientSetter,
                                  @Nullable Supplier<int[]> serverGetter, @Nullable Consumer<int[]> serverSetter) {
        if (clientGetter == null && serverGetter == null) {
            throw new NullPointerException("Client or server getter must not be null!");
        }
        if (NetworkUtils.isClient()) {
            this.getter = clientGetter != null ? clientGetter : serverGetter;
            this.setter = clientSetter != null ? clientSetter : serverSetter;
        } else {
            this.getter = serverGetter != null ? serverGetter : clientGetter;
            this.setter = serverSetter != null ? serverSetter : clientSetter;
        }
        this.cache = this.getter.get().clone();
    }

    @Override
    public void setValue(int @NotNull [] value, boolean setSource, boolean sync) {
        if (value.length != cache.length) {
            throw new IllegalArgumentException("Incompatible array lengths");
        }
        System.arraycopy(value, 0, cache, 0, value.length);
        if (setSource && this.setter != null) {
            this.setter.accept(value);
        }
        onValueChanged();
        if (sync) {
            sync();
        }
    }

    @Override
    public boolean updateCacheFromSource(boolean isFirstSync) {
        int[] source = this.getter.get();
        if (isFirstSync || !Arrays.equals(source, this.cache)) {
            setValue(source, false, false);
            return true;
        }
        return false;
    }

    @Override
    public void notifyUpdate() {
        setValue(this.getter.get(), false, true);
    }

    @Override
    public void write(@NotNull PacketBuffer buffer) throws IOException {
        for (int i : cache) {
            buffer.writeVarInt(i);
        }
    }

    @Override
    public void read(@NotNull PacketBuffer buffer) throws IOException {
        int[] value = new int[cache.length];
        for (int i = 0; i < value.length; i++) {
            value[i] = buffer.readVarInt();
        }
        setValue(value, true, false);
    }

    @Override
    public int[] getValue() {
        return this.cache;
    }

    @Override
    public Class<int[]> getValueType() {
        return int[].class;
    }

    public int getValue(int index) {
        return this.cache[index];
    }
}

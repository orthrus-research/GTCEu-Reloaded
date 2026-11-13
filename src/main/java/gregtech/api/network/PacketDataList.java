package gregtech.api.network;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * An optimised data structure backed by two arrays.
 * This is essentially equivalent to <code>List<Pair<Integer, byte[]>></code>, but more efficient.
 * {@link it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap} can not be used since it doesn't allow duplicate
 * discriminators.
 */
public class PacketDataList {

    private int[] discriminators;
    private byte[][] data;
    private int size = 0;

    public PacketDataList() {
        this.discriminators = new int[4];
        this.data = new byte[4][];
    }

    /**
     * Resizes the arrays to fit the required elements.
     *
     * @param s minimum size
     */
    private void ensureSize(int s) {
        if (this.discriminators.length < s) {
            int n = this.discriminators.length;
            int newCapacity = Math.max(n + 2, s); // there are rarely more than 2 elements in the list
            int[] temp = new int[newCapacity];
            byte[][] temp2 = new byte[newCapacity][];
            System.arraycopy(this.discriminators, 0, temp, 0, n);
            System.arraycopy(this.data, 0, temp2, 0, n);
            this.discriminators = temp;
            this.data = temp2;
        }
    }

    /**
     * Adds a discriminator - data pair to the list
     *
     * @param discriminator data id
     * @param data          data
     */
    public void add(int discriminator, byte[] data) {
        ensureSize(this.size + 1);
        this.discriminators[this.size] = discriminator;
        this.data[this.size] = data;
        this.size++;
    }

    /**
     * Adds all discriminator - data pairs from another list.
     * This does not check if the other list is empty or the same list.
     *
     * @param dataList other data list
     */
    public void addAll(PacketDataList dataList) {
        ensureSize(this.size + dataList.size);
        System.arraycopy(dataList.discriminators, 0, this.discriminators, this.size, dataList.size);
        System.arraycopy(dataList.data, 0, this.data, this.size, dataList.size);
        this.size += dataList.size;
    }

    /**
     * @return amount of data packets
     */
    public int size() {
        return size;
    }

    /**
     * @return true if there are no data packets
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * remove all data packets
     */
    public void clear() {
        Arrays.fill(this.discriminators, 0);
        Arrays.fill(this.data, null);
        this.size = 0;
    }

    /**
     * Writes all discriminator - data pairs to a nbt list.
     * Also removes all data packets from this list.
     *
     * @return nbt list with discriminators and data
     */
    /**
     * Write the updates as one binary list, discriminator and length before each, and clear the list.
     *
     * @return the encoded updates, read back with {@link #forEachEncoded}
     */
    public byte @NotNull [] dumpToBytes() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeVarInt(this.size);
        for (int i = 0; i < this.size; i++) {
            buffer.writeVarInt(this.discriminators[i]);
            buffer.writeVarInt(this.data[i].length);
            buffer.writeBytes(this.data[i]);
            this.data[i] = null;
        }
        this.size = 0;
        return Arrays.copyOfRange(buffer.array(), 0, buffer.writerIndex());
    }

    /** Receives one decoded update: its discriminator and a buffer over its bytes. */
    @FunctionalInterface
    public interface EncodedUpdateConsumer {

        void accept(int discriminator, @NotNull PacketBuffer data);
    }

    /**
     * Decode a list written by {@link #dumpToBytes}, handing each update a buffer over its own bytes without
     * copying them.
     */
    public static void forEachEncoded(byte @NotNull [] encoded, @NotNull EncodedUpdateConsumer consumer) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(encoded));
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            int discriminator = buffer.readVarInt();
            int length = buffer.readVarInt();
            consumer.accept(discriminator, new PacketBuffer(buffer.readSlice(length)));
        }
    }

    @NotNull
    public NBTTagList dumpToNbt() {
        NBTTagList listTag = new NBTTagList();
        for (int i = 0; i < this.size; i++) {
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setByteArray(Integer.toString(this.discriminators[i]), this.data[i]);
            listTag.appendTag(entryTag);
            this.data[i] = null;
        }
        this.size = 0;
        return listTag;
    }
}

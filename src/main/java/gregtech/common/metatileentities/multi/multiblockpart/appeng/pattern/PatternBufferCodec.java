package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Checked, large-count storage. Vanilla ItemStack's byte Count is not an amount ledger. */
public final class PatternBufferCodec {

    public static final int MAX_PATTERNS = 27;
    public static final int MAX_BATCHES = 27;
    public static final int MAX_ENTRIES = 100;
    public static final int MAX_BATCH_BYTES = 64 * 1024;
    public static final int MAX_PORTABLE_BYTES = 1024 * 1024;
    public static final int MAX_STACK_BYTES = 8 * 1024;

    private PatternBufferCodec() {}

    public static NBTTagCompound writeItem(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!stack.isEmpty()) {
            ItemStack identity = stack.copy();
            identity.setCount(1);
            NBTTagCompound encodedIdentity = identity.writeToNBT(new NBTTagCompound());
            // AE2 UEL transforms ItemStack's Count writer; this identity schema remains canonical.
            encodedIdentity.setByte("Count", (byte) 1);
            tag.setTag("Stack", encodedIdentity);
            tag.setInteger("Amount", stack.getCount());
        }
        return tag;
    }

    public static ItemStack readItem(NBTTagCompound tag) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        require(tag.hasKey("Stack", 10) && tag.hasKey("Amount", 3), "Malformed item entry");
        int amount = tag.getInteger("Amount");
        require(amount > 0, "Nonpositive item amount");
        NBTTagCompound identity = tag.getCompoundTag("Stack");
        boolean unitCount = identity.hasKey("Count", 1) && identity.getByte("Count") == 1 ||
                identity.hasKey("Count", 3) && identity.getInteger("Count") == 1;
        require(identity.hasKey("id", 8) && identity.hasKey("Damage", 2) &&
                unitCount &&
                (!identity.hasKey("tag") || identity.hasKey("tag", 10)) &&
                (!identity.hasKey("ForgeCaps") || identity.hasKey("ForgeCaps", 10)), "Malformed item identity");
        ItemStack stack = new ItemStack(identity.copy());
        require(!stack.isEmpty(), "Unknown item identity");
        stack.setCount(amount);
        return stack;
    }

    public static FluidStack readFluid(NBTTagCompound tag) {
        if (tag.isEmpty()) return null;
        require(tag.hasKey("FluidName", 8) && tag.hasKey("Amount", 3) &&
                (!tag.hasKey("Tag") || tag.hasKey("Tag", 10)), "Malformed fluid entry");
        require(tag.getInteger("Amount") > 0 && FluidRegistry.getFluid(tag.getString("FluidName")) != null,
                "Invalid fluid identity or amount");
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.copy());
        require(fluid != null && fluid.amount > 0, "Invalid fluid entry");
        return fluid;
    }

    public static NBTTagList list(NBTTagCompound tag, String key, int limit) {
        require(tag.hasKey(key, 9), "Missing list " + key);
        NBTTagList list = (NBTTagList) tag.getTag(key);
        require((list.tagCount() == 0 || list.getTagType() == 10) && list.tagCount() <= limit,
                "Malformed or oversized list " + key);
        return list;
    }

    public static boolean fits(NBTTagCompound tag, int limit) {
        try {
            CompressedStreamTools.write(tag, new DataOutputStream(new LimitedCounter(limit)));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static final class LimitedCounter extends OutputStream {

        private final int limit;
        private int count;

        private LimitedCounter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            if (this.count == limit) throw new IOException("Pattern buffer size limit");
            this.count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > limit - count) throw new IOException("Pattern buffer size limit");
            this.count += length;
        }
    }
}

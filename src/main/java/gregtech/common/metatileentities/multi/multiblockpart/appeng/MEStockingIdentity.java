package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Deterministic tie keys, independent of compound map order, quantity and runtime item IDs. */
final class MEStockingIdentity {

    private MEStockingIdentity() {}

    static String item(IAEItemStack stack) {
        ItemStack identity = stack.copy().setStackSize(1).createItemStack();
        return identity.getItem().getRegistryName() + "\0" +
                String.format(Locale.ROOT, "%08x", identity.getItemDamage() ^ Integer.MIN_VALUE) + "\0" +
                nbt(identity.serializeNBT());
    }

    static String fluid(IAEFluidStack stack) {
        FluidStack identity = stack.copy().setStackSize(1).getFluidStack();
        return identity.getFluid().getName() + "\0" + nbt(identity.writeToNBT(new NBTTagCompound()));
    }

    static String nbt(NBTBase tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(tag, output);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            // The output is in memory; no network or filesystem is involved.
            throw new UncheckedIOException(exception);
        }
    }

    private static void write(NBTBase tag, DataOutputStream output) throws IOException {
        if (tag == null) {
            output.writeByte(-1);
            return;
        }
        output.writeByte(tag.getId());
        if (tag instanceof NBTTagCompound compound) {
            List<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            output.writeInt(keys.size());
            for (String key : keys) {
                writeString(key, output);
                write(compound.getTag(key), output);
            }
        } else if (tag instanceof NBTTagList list) {
            output.writeByte(list.getTagType());
            output.writeInt(list.tagCount());
            for (int i = 0; i < list.tagCount(); i++) {
                write(list.get(i), output);
            }
        } else if (tag instanceof NBTTagString string) {
            writeString(string.getString(), output);
        } else {
            // Scalar/array payloads have no map ordering. A single-key vanilla NBT wrapper
            // preserves their exact numeric types and supports 1.12's getter-less long arrays.
            NBTTagCompound scalar = new NBTTagCompound();
            scalar.setTag("", tag);
            CompressedStreamTools.write(scalar, output);
        }
    }

    private static void writeString(String value, DataOutputStream output) throws IOException {
        output.writeInt(value.length());
        for (int i = 0; i < value.length(); i++) {
            output.writeChar(value.charAt(i));
        }
    }
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import org.jetbrains.annotations.Nullable;

/**
 * What the ME parts need to know about the stacks of one AE2 storage channel, so that each item part and its fluid
 * twin can share their code. {@code N} is the Minecraft stack a multiblock matches recipes against.
 */
abstract class MEStackType<T extends IAEStack<T>, N> {

    static final MEStackType<IAEItemStack, ItemStack> ITEMS = new MEStackType<>(IItemStorageChannel.class) {

        @Override
        IAEItemStack unwrap(IAEItemStack stack) {
            return stack instanceof WrappedItemStack wrapped ? wrapped.getAEStack() : stack;
        }

        @Override
        IAEItemStack request(IAEItemStack config) {
            return config instanceof WrappedItemStack wrapped ? wrapped.getAEStack() : config.copy();
        }

        @Override
        void writeConfig(IAEItemStack config, NBTTagCompound tag) {
            config.getDefinition().writeToNBT(tag);
        }

        @Override
        IAEItemStack readConfig(NBTTagCompound tag) {
            return WrappedItemStack.fromNBT(tag);
        }

        @Override
        ItemStack toNative(IAEItemStack stack) {
            ItemStack itemStack = stack.createItemStack();
            return itemStack == null || itemStack.isEmpty() ? null : itemStack;
        }

        @Override
        IAEItemStack wrap(ItemStack stack) {
            return stack == null ? null : WrappedItemStack.fromItemStack(stack);
        }

        @Override
        String identity(IAEItemStack stack) {
            return MEStockingIdentity.item(stack);
        }
    };

    static final MEStackType<IAEFluidStack, FluidStack> FLUIDS = new MEStackType<>(IFluidStorageChannel.class) {

        @Override
        IAEFluidStack unwrap(IAEFluidStack stack) {
            return stack instanceof WrappedFluidStack wrapped ? wrapped.getAEStack() : stack;
        }

        @Override
        IAEFluidStack request(IAEFluidStack config) {
            return config instanceof WrappedFluidStack wrapped ? wrapped.getAEStack() : config.copy();
        }

        @Override
        void writeConfig(IAEFluidStack config, NBTTagCompound tag) {
            config.writeToNBT(tag);
        }

        @Override
        IAEFluidStack readConfig(NBTTagCompound tag) {
            return WrappedFluidStack.fromNBT(tag);
        }

        @Override
        FluidStack toNative(IAEFluidStack stack) {
            return stack.getFluidStack();
        }

        @Override
        IAEFluidStack wrap(FluidStack stack) {
            return WrappedFluidStack.fromFluidStack(stack);
        }

        @Override
        String identity(IAEFluidStack stack) {
            return MEStockingIdentity.fluid(stack);
        }
    };

    private final Class<? extends IStorageChannel<T>> storageChannel;

    private MEStackType(Class<? extends IStorageChannel<T>> storageChannel) {
        this.storageChannel = storageChannel;
    }

    Class<? extends IStorageChannel<T>> getStorageChannel() {
        return storageChannel;
    }

    /** The AE2 stack a GT wrapper stands for; any other stack as it is. */
    @Nullable
    abstract T unwrap(@Nullable T stack);

    /** A fresh stack of the config's resource for asking the network, which may change it. */
    abstract T request(T config);

    /** Writes a config slot for a data stick. */
    abstract void writeConfig(T config, NBTTagCompound tag);

    @Nullable
    abstract T readConfig(NBTTagCompound tag);

    /** The Minecraft stack for this one, or null when there is none. */
    @Nullable
    abstract N toNative(T stack);

    /** A GT-wrapped stack, the form the config widgets decode; null for null. */
    @Nullable
    abstract T wrap(@Nullable N stack);

    /** A tie-break key for stocking selection; see {@link MEStockingIdentity}. */
    abstract String identity(T stack);
}

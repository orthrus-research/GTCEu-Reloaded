package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.util.FluidTooltipUtil;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.RichTooltip;
import org.jetbrains.annotations.Nullable;

/**
 * What the ME parts need to know about the stacks of one AE2 storage channel, so that each item part and its fluid
 * twin can share their code, their screens included. {@code N} is the Minecraft stack a multiblock matches recipes
 * against.
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

        @Override
        void writeShown(PacketBuffer buf, IAEItemStack stack) {
            ItemStack resource = stack.createItemStack();
            resource.setCount(1);
            NetworkUtils.writeItemStack(buf, resource);
            buf.writeVarLong(stack.getStackSize());
        }

        @Override
        IAEItemStack readShown(PacketBuffer buf) {
            ItemStack resource = NetworkUtils.readItemStack(buf);
            long amount = buf.readVarLong();
            IAEItemStack stack = resource.isEmpty() ? null : AEItemStack.fromItemStack(resource);
            return stack == null ? null : stack.setStackSize(amount);
        }

        @Override
        void writeNative(PacketBuffer buf, ItemStack stack) {
            NetworkUtils.writeItemStack(buf, stack);
        }

        @Override
        ItemStack readNative(PacketBuffer buf) {
            ItemStack stack = NetworkUtils.readItemStack(buf);
            return stack.isEmpty() ? null : stack;
        }

        @Override
        ItemStack fromCursor(ItemStack cursor) {
            return cursor.isEmpty() ? null : cursor.copy();
        }

        @Override
        ItemStack fromIngredient(Object ingredient) {
            return ingredient instanceof ItemStack stack && !stack.isEmpty() ? stack.copy() : null;
        }

        @Override
        IDrawable[] configBackground(boolean autoPull) {
            return autoPull ? new IDrawable[] { GTGuiTextures.SLOT_DARK, GTGuiTextures.CONFIG_ARROW } :
                    new IDrawable[] { GTGuiTextures.SLOT, GTGuiTextures.CONFIG_ARROW_DARK };
        }

        @Override
        void draw(IAEItemStack stack, int x, int y) {
            ItemStack shown = stack.createItemStack();
            shown.setCount(1);
            GuiDraw.drawItem(shown, x, y, 16, 16, 0);
        }

        @Override
        IDrawable slotBackground() {
            return GTGuiTextures.SLOT;
        }

        @Override
        String unit() {
            return "";
        }

        @Override
        void addTooltip(RichTooltip tooltip, IAEItemStack stack, boolean withAmount) {
            tooltip.addFromItem(stack.createItemStack());
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

        @Override
        void writeShown(PacketBuffer buf, IAEFluidStack stack) {
            FluidStack resource = stack.getFluidStack().copy();
            resource.amount = 1;
            NetworkUtils.writeFluidStack(buf, resource);
            buf.writeVarLong(stack.getStackSize());
        }

        @Override
        IAEFluidStack readShown(PacketBuffer buf) {
            FluidStack resource = NetworkUtils.readFluidStack(buf);
            long amount = buf.readVarLong();
            IAEFluidStack stack = resource == null ? null : AEFluidStack.fromFluidStack(resource);
            return stack == null ? null : stack.setStackSize(amount);
        }

        @Override
        void writeNative(PacketBuffer buf, FluidStack stack) {
            NetworkUtils.writeFluidStack(buf, stack);
        }

        @Override
        FluidStack readNative(PacketBuffer buf) {
            FluidStack stack = NetworkUtils.readFluidStack(buf);
            return stack == null || stack.amount <= 0 ? null : stack;
        }

        @Override
        FluidStack fromCursor(ItemStack cursor) {
            return FluidUtil.getFluidContained(cursor);
        }

        @Override
        FluidStack fromIngredient(Object ingredient) {
            FluidStack stack = GTUtility.getFluidFromContainer(ingredient);
            return stack == null ? null : stack.copy();
        }

        @Override
        IDrawable[] configBackground(boolean autoPull) {
            return new IDrawable[] { autoPull ? GTGuiTextures.SLOT_DARK : GTGuiTextures.FLUID_SLOT,
                    GTGuiTextures.CONFIG_ARROW };
        }

        @Override
        void draw(IAEFluidStack stack, int x, int y) {
            GuiDraw.drawFluidTexture(stack.getFluidStack(), x, y, 17, 17, 0);
        }

        @Override
        IDrawable slotBackground() {
            return GTGuiTextures.FLUID_SLOT;
        }

        @Override
        String unit() {
            return "L";
        }

        @Override
        void addTooltip(RichTooltip tooltip, IAEFluidStack stack, boolean withAmount) {
            FluidStack fluid = stack.getFluidStack();
            tooltip.addLine(KeyUtil.fluid(fluid));
            if (withAmount) {
                tooltip.addLine(IKey.str(String.format("%,d L", stack.getStackSize())));
            }
            FluidTooltipUtil.handleFluidTooltip(tooltip, fluid);
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

    /** A GT-wrapped stack, the form configs are kept in; null for null. */
    @Nullable
    abstract T wrap(@Nullable N stack);

    /** A tie-break key for stocking selection; see {@link MEStockingIdentity}. */
    abstract String identity(T stack);

    /** Writes a slot's stack for a screen to show: its resource once, then its amount, which may exceed an int. */
    abstract void writeShown(PacketBuffer buf, T stack);

    @Nullable
    abstract T readShown(PacketBuffer buf);

    /** Writes a Minecraft stack a screen sends to configure a slot. */
    abstract void writeNative(PacketBuffer buf, N stack);

    @Nullable
    abstract N readNative(PacketBuffer buf);

    /** What clicking a config slot with this stack on the cursor configures, or null for nothing. */
    @Nullable
    abstract N fromCursor(ItemStack cursor);

    /** What a recipe viewer ingredient dropped on a config slot configures, or null for nothing. */
    @Nullable
    abstract N fromIngredient(Object ingredient);

    /** The textures under a config slot, darker while auto-pull manages it. */
    abstract IDrawable[] configBackground(boolean autoPull);

    /** The texture of a slot showing this channel's stacks. */
    abstract IDrawable slotBackground();

    /** Draws a stack's resource in a slot whose inside starts at {@code x}, {@code y}. */
    abstract void draw(T stack, int x, int y);

    /** The unit after an amount: none for items, litres for fluids. */
    abstract String unit();

    abstract void addTooltip(RichTooltip tooltip, T stack, boolean withAmount);
}

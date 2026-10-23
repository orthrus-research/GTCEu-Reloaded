package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.capability.impl.FluidTankList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferCodec.*;

/** One accepted medium handoff, owning only its remaining physical inputs. */
public final class PatternBufferBatch {

    private final long id;
    private final int pattern;
    private final PatternBufferItems items;
    private final PatternBufferTank[] tanks;
    private final FluidTankList fluids;
    private boolean refundPending;
    private boolean initializing = true;

    PatternBufferBatch(long id, int pattern, List<ItemStack> items, List<FluidStack> fluids, Runnable changed) {
        require(id > 0 && pattern >= 0 && pattern < MAX_PATTERNS, "Invalid batch identity");
        require(items.size() <= MAX_ENTRIES && fluids.size() <= MAX_ENTRIES, "Too many batch entries");
        this.id = id;
        this.pattern = pattern;
        Runnable onChanged = () -> { if (!initializing) changed.run(); };
        this.items = new PatternBufferItems(items.size(), Integer.MAX_VALUE, true, onChanged,
                (slot, stack) -> !refundPending);
        for (int i = 0; i < items.size(); i++) this.items.restore(i, items.get(i));
        this.tanks = new PatternBufferTank[fluids.size()];
        for (int i = 0; i < fluids.size(); i++) {
            this.tanks[i] = new PatternBufferTank(Integer.MAX_VALUE, true, onChanged, () -> !refundPending);
            this.tanks[i].restore(fluids.get(i));
        }
        this.fluids = new FluidTankList(true, tanks);
        this.initializing = false;
    }

    public long getId() {
        return id;
    }

    public int getPattern() {
        return pattern;
    }

    public PatternBufferItems getItems() {
        return items;
    }

    public FluidTankList getFluids() {
        return fluids;
    }

    public boolean isRefundPending() {
        return refundPending;
    }

    void setRefundPending(boolean value) {
        this.refundPending = value;
    }

    public boolean isEmpty() {
        for (int i = 0; i < items.getSlots(); i++) if (!items.getStackInSlot(i).isEmpty()) return false;
        for (PatternBufferTank tank : tanks) if (tank.getFluidAmount() > 0) return false;
        return true;
    }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("Id", id);
        tag.setInteger("Pattern", pattern);
        tag.setBoolean("Refund", refundPending);
        NBTTagList itemList = new NBTTagList();
        for (int i = 0; i < items.getSlots(); i++) itemList.appendTag(writeItem(items.getStackInSlot(i)));
        tag.setTag("Items", itemList);
        NBTTagList fluidList = new NBTTagList();
        for (PatternBufferTank tank : tanks) {
            FluidStack fluid = tank.getFluid();
            fluidList.appendTag(fluid == null ? new NBTTagCompound() : fluid.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("Fluids", fluidList);
        return tag;
    }

    /** Refund sink returns accepted amounts. Each entry is attempted once, with every remainder retained. */
    void refund(PatternBufferRefund sink) {
        if (!refundPending) return;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int accepted = sink.insertItem(stack.copy());
            require(accepted >= 0 && accepted <= stack.getCount(), "Invalid item refund result");
            if (accepted > 0) {
                stack.shrink(accepted);
                items.restore(i, stack);
            }
        }
        for (PatternBufferTank tank : tanks) {
            FluidStack fluid = tank.getFluid();
            if (fluid == null) continue;
            int accepted = sink.insertFluid(fluid.copy());
            require(accepted >= 0 && accepted <= fluid.amount, "Invalid fluid refund result");
            if (accepted > 0) {
                fluid.amount -= accepted;
                tank.restore(fluid.amount == 0 ? null : fluid);
            }
        }
    }
}

package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.gui.widgets.SlotWidget;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;

/** Avoids Forge's temporary clear/restore slot probes, which detach native mutable backing stacks. */
final class PatternBufferSlotWidget extends SlotWidget {

    private final PatternBufferGuiItems items;
    private final int index;
    private final MetaTileEntityMEPatternBuffer buffer;
    private final EntityPlayer player;

    PatternBufferSlotWidget(PatternBufferGuiItems items, int index, int x, int y,
                            MetaTileEntityMEPatternBuffer buffer, EntityPlayer player) {
        super(items, index, x, y);
        this.items = items;
        this.index = index;
        this.buffer = buffer;
        this.player = player;
    }

    @Override
    protected Slot createSlot(IItemHandler handler, int slot, boolean canShiftClickInto) {
        return new WidgetSlotItemHandler(handler, slot, 0, 0, canShiftClickInto) {

            @Override
            public boolean isItemValid(@NotNull ItemStack stack) {
                return PatternBufferSlotWidget.this.canPutStack(stack) && handler.isItemValid(slot, stack);
            }

            @Override
            public int getItemStackLimit(@NotNull ItemStack stack) {
                return handler.isItemValid(slot, stack) ?
                        Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize()) : 0;
            }
        };
    }

    @Override
    public void onSlotChanged() {
        items.onSlotChanged(index);
        super.onSlotChanged();
    }

    @Override
    public boolean canPutStack(ItemStack stack) {
        return super.canPutStack(stack) && (player.world.isRemote || buffer.canAccess(player));
    }

    @Override
    public boolean canTakeStack(EntityPlayer who) {
        return super.canTakeStack(who) && (who.world.isRemote || buffer.canAccess(who));
    }
}

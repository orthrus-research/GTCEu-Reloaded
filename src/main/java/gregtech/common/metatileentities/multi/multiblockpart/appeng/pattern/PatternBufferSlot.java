package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.NotNull;

/**
 * A container slot of the pattern buffer. Its checks avoid Forge's temporary clear and restore probes, which detach
 * native mutable backing stacks; edits a container makes in place reach the storage through {@link #onSlotChanged}.
 * A player who may not access the buffer can neither put nor take.
 */
final class PatternBufferSlot extends ModularSlot {

    private final PatternBufferGuiItems items;
    private final int index;
    private final MetaTileEntityMEPatternBuffer buffer;

    PatternBufferSlot(PatternBufferGuiItems items, int index, MetaTileEntityMEPatternBuffer buffer) {
        super(items, index);
        this.items = items;
        this.index = index;
        this.buffer = buffer;
    }

    private boolean canAccess(EntityPlayer player) {
        return player.world.isRemote || buffer.canAccess(player);
    }

    @Override
    public boolean isItemValid(@NotNull ItemStack stack) {
        // a slot outside an open container has no player to check
        return !stack.isEmpty() && isCanPut() && (!isInitialized() || canAccess(getPlayer())) &&
                items.isItemValid(index, stack);
    }

    @Override
    public int getItemStackLimit(@NotNull ItemStack stack) {
        return items.isItemValid(index, stack) ? Math.min(items.getSlotLimit(index), stack.getMaxStackSize()) : 0;
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return super.canTakeStack(player) && canAccess(player);
    }

    @Override
    public void onSlotChanged() {
        items.onSlotChanged(index);
        super.onSlotChanged();
    }
}

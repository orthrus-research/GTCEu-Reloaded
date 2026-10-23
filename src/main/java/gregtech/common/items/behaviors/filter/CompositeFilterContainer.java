package gregtech.common.items.behaviors.filter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import com.cleanroommc.modularui.screen.ModularContainer;
import org.jetbrains.annotations.NotNull;

/** Keeps a physical item inventory attached to the exact held stack and open server container. */
public final class CompositeFilterContainer extends ModularContainer {

    private final EntityPlayer owner;
    private final EnumHand hand;
    private final ItemStack parent;
    private final int parentHotbarSlot;

    public CompositeFilterContainer(EntityPlayer owner, EnumHand hand, ItemStack parent) {
        this.owner = owner;
        this.hand = hand;
        this.parent = parent;
        this.parentHotbarSlot = hand == EnumHand.MAIN_HAND ? owner.inventory.currentItem : -1;
    }

    public int getParentHotbarSlot() {
        return parentHotbarSlot;
    }

    public boolean ownsParent() {
        // Client inventory sync replaces stacks. The server alone owns the authoritative object/session identity.
        if (owner.world.isRemote) return true;
        return owner.openContainer == this && owner.getHeldItem(hand) == parent && parent.getCount() == 1 &&
                (parentHotbarSlot == -1 || owner.inventory.currentItem == parentHotbarSlot);
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer player) {
        return player == owner && ownsParent();
    }

    private boolean isParentSlot(int slotId) {
        return slotId >= 0 && slotId < inventorySlots.size() && inventorySlots.get(slotId).getStack() == parent;
    }

    @Override
    public @NotNull ItemStack slotClick(int slotId, int button, @NotNull ClickType clickType,
                                        @NotNull EntityPlayer player) {
        if (!canInteractWith(player) || isParentSlot(slotId) ||
                clickType == ClickType.SWAP && parentHotbarSlot >= 0 && button == parentHotbarSlot) {
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack transferStackInSlot(@NotNull EntityPlayer player, int index) {
        if (!canInteractWith(player) || isParentSlot(index)) return ItemStack.EMPTY;
        return super.transferStackInSlot(player, index);
    }
}

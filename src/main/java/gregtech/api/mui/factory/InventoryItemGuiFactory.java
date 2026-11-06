package gregtech.api.mui.factory;

import gregtech.api.items.metaitem.MetaItem;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.utils.Platform;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Inventory/slot UI support adapted from SussyPatches, LGPL-3.0; see NOTICE.md. */
public final class InventoryItemGuiFactory extends AbstractUIFactory<PlayerInventoryGuiData> {

    public static final InventoryItemGuiFactory INSTANCE = new InventoryItemGuiFactory();

    private InventoryItemGuiFactory() {
        super("gregtech:inventory_item");
    }

    public static void open(EntityPlayer player, InventoryType inventory, int slot) {
        var serverPlayer = verifyServerSide(Objects.requireNonNull(player, "player"));
        GuiManager.open(INSTANCE, data(player, inventory, slot), serverPlayer);
    }

    public static void openFromPlayerInventory(EntityPlayer player, int slot) {
        open(player, InventoryTypes.PLAYER, slot);
    }

    public static void openFromHand(EntityPlayer player, EnumHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        openFromPlayerInventory(player, hand == EnumHand.OFF_HAND ? 40 : player.inventory.currentItem);
    }

    public static void openFromBaubles(EntityPlayer player, int slot) {
        open(player, InventoryTypes.BAUBLES, slot);
    }

    @SideOnly(Side.CLIENT)
    public static void openClient(InventoryType inventory, int slot) {
        var player = Platform.getClientPlayer();
        verifyClientSide(player);
        GuiManager.openFromClient(INSTANCE, data(player, inventory, slot));
    }

    @SideOnly(Side.CLIENT)
    public static void openFromPlayerInventoryClient(int slot) {
        openClient(InventoryTypes.PLAYER, slot);
    }

    @SideOnly(Side.CLIENT)
    public static void openFromHandClient(EnumHand hand) {
        Objects.requireNonNull(hand, "hand");
        openFromPlayerInventoryClient(
                hand == EnumHand.OFF_HAND ? 40 : Platform.getClientPlayer().inventory.currentItem);
    }

    @SideOnly(Side.CLIENT)
    public static void openFromBaublesClient(int slot) {
        openClient(InventoryTypes.BAUBLES, slot);
    }

    private static PlayerInventoryGuiData data(EntityPlayer player, InventoryType inventory, int slot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(inventory, "inventory");
        if (InventoryType.getFromId(inventory.getId()) != inventory || !inventory.isActive()) {
            throw new IllegalArgumentException("Inventory is not registered and active: " + inventory.getId());
        }
        if (slot < 0 || slot >= inventory.getSlotCount(player)) {
            throw new IllegalArgumentException("Slot outside inventory: " + slot);
        }
        return new PlayerInventoryGuiData(player, inventory, slot);
    }

    @Override
    public @NotNull IGuiHolder<PlayerInventoryGuiData> getGuiHolder(PlayerInventoryGuiData context) {
        var checked = data(context.getPlayer(), context.getInventoryType(), context.getSlotIndex());
        var stack = checked.getUsedItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof MetaItem<?> item)) {
            throw new IllegalArgumentException("Inventory slot does not contain a MetaItem");
        }
        var value = item.getItem(stack);
        if (value == null || value.getInventoryUIManager() == null) {
            throw new IllegalArgumentException("MetaItem has no inventory UI provider");
        }
        return value.getInventoryUIManager();
    }

    @Override
    public void writeGuiData(PlayerInventoryGuiData context, PacketBuffer buffer) {
        data(context.getPlayer(), context.getInventoryType(), context.getSlotIndex());
        context.getInventoryType().write(buffer);
        buffer.writeVarInt(context.getSlotIndex());
    }

    @Override
    public @NotNull PlayerInventoryGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return data(player, InventoryType.read(buffer), buffer.readVarInt());
    }
}

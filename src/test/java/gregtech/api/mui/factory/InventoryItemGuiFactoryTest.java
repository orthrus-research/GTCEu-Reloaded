package gregtech.api.mui.factory;

import gregtech.Bootstrap;
import gregtech.api.items.gui.InventoryItemUIFactory;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.StandardMetaItem;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;

import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemGuiFactoryTest {

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
        InventoryTypes.init();
    }

    @Test
    void independentProvidersResolveWithoutReplacingTheHeldItemContract() {
        var player = player();
        var item = new StandardMetaItem();
        InventoryItemUIFactory inventory = (data, manager, settings) -> ModularPanel.defaultPanel("inventory_test");
        ItemUIFactory held = new ItemUIFactory() {};
        var value = item.addItem(0, "inventory_ui_test").addComponents(inventory, held);
        player.inventory.setInventorySlotContents(14, value.getStackForm());
        player.setHeldItem(EnumHand.OFF_HAND, value.getStackForm());
        assertSame(inventory, value.getInventoryUIManager());
        assertSame(held, value.getUIManager());
        assertSame(inventory, InventoryItemGuiFactory.INSTANCE.getGuiHolder(
                new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 14)));
        assertSame(held, MetaItemGuiFactory.INSTANCE.getGuiHolder(new HandGuiData(player, EnumHand.OFF_HAND)));
        assertEquals("gregtech:inventory_item", InventoryItemGuiFactory.INSTANCE.getFactoryName());
        assertEquals("gregtech:meta_item", MetaItemGuiFactory.INSTANCE.getFactoryName());
    }

    @Test
    void slotPacketsRoundTripAcrossMainInventoryArmorAndOffhand() {
        var player = player();
        for (int slot : new int[] { 0, 8, 9, 35, 36, 40 }) {
            ItemStack stack = new ItemStack(Items.APPLE, slot + 1);
            player.inventory.setInventorySlotContents(slot, stack);
            var context = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, slot);
            var buffer = new PacketBuffer(Unpooled.buffer());
            try {
                InventoryItemGuiFactory.INSTANCE.writeGuiData(context, buffer);
                buffer.writeInt(0x12345678);
                var restored = InventoryItemGuiFactory.INSTANCE.readGuiData(player, buffer);
                assertSame(InventoryTypes.PLAYER, restored.getInventoryType());
                assertEquals(slot, restored.getSlotIndex());
                player.inventory.currentItem = (slot + 1) % 9;
                assertSame(stack, restored.getUsedItemStack(), "Changing held selection must not redirect this UI");
                assertEquals(0x12345678, buffer.readInt());
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void invalidSlotsAreRejectedBeforeEncodingOrInventoryAccess() {
        var player = player();
        var inventory = new TestInventory();
        for (int slot : new int[] { -1, 3, Integer.MAX_VALUE }) {
            var buffer = new PacketBuffer(Unpooled.buffer());
            try {
                assertThrows(IllegalArgumentException.class, () -> InventoryItemGuiFactory.INSTANCE.writeGuiData(
                        new PlayerInventoryGuiData(player, inventory, slot), buffer));
                assertEquals(0, buffer.writerIndex());
                inventory.write(buffer);
                buffer.writeVarInt(slot);
                assertThrows(IllegalArgumentException.class,
                        () -> InventoryItemGuiFactory.INSTANCE.readGuiData(player, buffer));
                assertEquals(0, inventory.reads);
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void optionalInventoriesUseTheirRegisteredContractAndInactiveOnesAreRejected() {
        var player = player();
        var inventory = new TestInventory();
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            var context = new PlayerInventoryGuiData(player, inventory, 2);
            InventoryItemGuiFactory.INSTANCE.writeGuiData(context, buffer);
            var restored = InventoryItemGuiFactory.INSTANCE.readGuiData(player, buffer);
            assertSame(inventory, restored.getInventoryType());
            assertEquals(2, restored.getSlotIndex());
            inventory.disabled = true;
            assertThrows(IllegalArgumentException.class,
                    () -> InventoryItemGuiFactory.INSTANCE.getGuiHolder(context));
            assertEquals(0, inventory.reads);
        } finally {
            buffer.release();
        }
        // Baubles follows the same active-inventory gate; this fixture does not claim a loaded Baubles runtime.
        if (!InventoryTypes.BAUBLES.isActive()) {
            assertThrows(IllegalArgumentException.class, () -> InventoryItemGuiFactory.INSTANCE.getGuiHolder(
                    new PlayerInventoryGuiData(player, InventoryTypes.BAUBLES, 0)));
        }
    }

    @Test
    void absentUnknownAndHeldOnlyProvidersDoNotOpenAsInventoryUis() {
        var player = player();
        var context = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 12);
        assertThrows(IllegalArgumentException.class, () -> InventoryItemGuiFactory.INSTANCE.getGuiHolder(context));
        player.inventory.setInventorySlotContents(12, new ItemStack(Items.APPLE));
        assertThrows(IllegalArgumentException.class, () -> InventoryItemGuiFactory.INSTANCE.getGuiHolder(context));
        var item = new StandardMetaItem();
        var value = item.addItem(0, "held_ui_only_test").addComponents(new ItemUIFactory() {});
        player.inventory.setInventorySlotContents(12, value.getStackForm());
        assertThrows(IllegalArgumentException.class, () -> InventoryItemGuiFactory.INSTANCE.getGuiHolder(context));
        player.inventory.setInventorySlotContents(12, new ItemStack(item, 1, 999));
        assertThrows(IllegalArgumentException.class, () -> InventoryItemGuiFactory.INSTANCE.getGuiHolder(context));
    }

    private static EntityPlayer player() {
        return new EntityPlayer(new DummyWorld(), new GameProfile(UUID.randomUUID(), "inventory_ui_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    private static final class TestInventory extends InventoryType {

        private boolean disabled;
        private int reads;

        private TestInventory() {
            super("gt_inventory_ui_test_" + UUID.randomUUID());
        }

        @Override
        public boolean isActive() {
            return !disabled;
        }

        @Override
        public int getSlotCount(EntityPlayer player) {
            return 3;
        }

        @Override
        public ItemStack getStackInSlot(EntityPlayer player, int slot) {
            reads++;
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(EntityPlayer player, int slot, ItemStack stack) {}
    }
}

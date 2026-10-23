package gregtech.common.items.behaviors.filter;

import gregtech.api.util.world.DummyWorld;
import gregtech.common.covers.filter.CompositeFilter;
import gregtech.common.covers.filter.CompositeFilterTestSupport;
import gregtech.common.covers.filter.IFilter.FilterType;
import gregtech.common.items.MetaItems;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static gregtech.common.covers.filter.CompositeFilterTestSupport.*;
import static gregtech.common.covers.filter.IFilter.FilterType.ITEM;
import static org.junit.jupiter.api.Assertions.*;

class CompositeFilterOwnershipTest {

    private static CompositeFilterTestSupport support;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        support = new CompositeFilterTestSupport();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        support.close();
    }

    @Test
    void parentPickupShiftAndNumberKeyRoutesAreRejectedWithoutChangingCursorOrInventory() {
        EntityPlayer player = player();
        ItemStack root = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT))).getContainerStack();
        player.inventory.currentItem = 2;
        player.setHeldItem(EnumHand.MAIN_HAND, root);
        CompositeFilterContainer container = new CompositeFilterContainer(player, EnumHand.MAIN_HAND, root);
        player.openContainer = container;
        container.inventorySlots.add(new Slot(player.inventory, 2, 0, 0));
        container.inventorySlots.add(new Slot(player.inventory, 3, 0, 0));
        ItemStack cursor = itemLeaf(new ItemStack(Items.GOLD_INGOT));
        player.inventory.setItemStack(cursor);
        NBTTagCompound before = root.writeToNBT(new NBTTagCompound());
        assertTrue(container.ownsParent());
        assertTrue(container.slotClick(0, 0, ClickType.PICKUP, player).isEmpty());
        assertTrue(container.slotClick(0, 0, ClickType.QUICK_MOVE, player).isEmpty());
        assertTrue(container.slotClick(1, 2, ClickType.SWAP, player).isEmpty());
        assertTrue(container.transferStackInSlot(player, 0).isEmpty());
        assertSame(cursor, player.inventory.getItemStack());
        assertSame(root, player.getHeldItem(EnumHand.MAIN_HAND));
        assertEquals(before, root.writeToNBT(new NBTTagCompound()));
    }

    @Test
    void replacedMainhandOrOffhandAndOldContainerRejectAllClickAndPhysicalMutationRoutes() {
        for (EnumHand hand : EnumHand.values()) {
            EntityPlayer player = player();
            CompositeFilter filter = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT)));
            ItemStack root = filter.getContainerStack();
            player.setHeldItem(hand, root);
            CompositeFilterContainer container = new CompositeFilterContainer(player, hand, root);
            player.openContainer = container;
            filter.getInventory().setOwner(container::ownsParent);
            assertTrue(filter.getInventory().canEdit());
            NBTTagCompound before = root.writeToNBT(new NBTTagCompound());
            ItemStack replacement = root.copy();
            player.setHeldItem(hand, replacement);
            ItemStack cursor = new ItemStack(Items.DIAMOND, 3);
            player.inventory.setItemStack(cursor);
            assertFalse(container.ownsParent());
            for (ClickType click : ClickType.values()) {
                assertTrue(container.slotClick(0, 0, click, player).isEmpty());
            }
            assertTrue(container.transferStackInSlot(player, 0).isEmpty());
            assertTrue(filter.getInventory().extractItem(0, 1, false).isEmpty());
            assertFalse(
                    filter.getInventory().insertItem(1, itemLeaf(new ItemStack(Items.GOLD_INGOT)), false).isEmpty());
            filter.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            assertSame(cursor, player.inventory.getItemStack());
            assertEquals(before, root.writeToNBT(new NBTTagCompound()));
            assertEquals(before, replacement.writeToNBT(new NBTTagCompound()));
            // Returning the same physical item does not revive an old editor after another container opened.
            player.setHeldItem(hand, root);
            player.openContainer = new CompositeFilterContainer(player, hand, root);
            assertTrue(filter.getInventory().extractItem(0, 1, false).isEmpty());
        }
    }

    @Test
    void hostedAndStaleEditorBlacklistPacketsCannotModifyTheParent() {
        CompositeFilter hosted = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT)));
        // Hosted instances are created independently from their physical parent, with no editor authorization.
        hosted = new CompositeFilter(hosted.getContainerStack(), ITEM);
        PanelSyncManager manager = new PanelSyncManager(false);
        hosted.createWidgets(manager);
        assertFalse(manager.getSlotGroup("composite_children").allowShiftTransfer());
        assertFalse(manager.getSlotGroup("composite_children").isAllowSorting());
        BooleanSyncValue blacklist = manager.findSyncHandler("composite_blacklist", BooleanSyncValue.class);
        send(blacklist, true);
        assertFalse(hosted.isBlacklistFilter());
        assertTrue(hosted.getInventory().extractItem(0, 1, false).isEmpty());

        EntityPlayer player = player();
        ItemStack root = hosted.getContainerStack();
        player.setHeldItem(EnumHand.MAIN_HAND, root);
        CompositeFilterContainer container = new CompositeFilterContainer(player, EnumHand.MAIN_HAND, root);
        player.openContainer = container;
        PanelSyncManager editable = new PanelSyncManager(false);
        hosted.createEditableWidgets(editable, container::ownsParent);
        BooleanSyncValue editableBlacklist = editable.findSyncHandler("composite_blacklist", BooleanSyncValue.class);
        send(editableBlacklist, true);
        assertTrue(hosted.isBlacklistFilter());
        player.setHeldItem(EnumHand.MAIN_HAND, root.copy());
        send(editableBlacklist, false);
        assertTrue(hosted.isBlacklistFilter());
    }

    @Test
    void handheldItemAndFluidTreesCollectSyncHandlersWithoutReregisteringNamedValues() {
        for (FilterType type : FilterType.values()) {
            CompositeFilter filter = editable(type);
            PanelSyncManager manager = new PanelSyncManager(false);
            ModularPanel panel = filter.createPanel(manager)
                    .child(filter.createEditableWidgets(manager, () -> true));
            BooleanSyncValue blacklist = manager.findSyncHandler("composite_blacklist", BooleanSyncValue.class);
            BooleanSyncValue valid = manager.findSyncHandler("composite_valid", BooleanSyncValue.class);
            assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
            assertSame(blacklist, manager.findSyncHandler("composite_blacklist", BooleanSyncValue.class));
            assertSame(valid, manager.findSyncHandler("composite_valid", BooleanSyncValue.class));
            CycleButtonWidget button = WidgetTree.findFirst(panel, CycleButtonWidget.class, widget -> true);
            assertNotNull(button);
            assertTrue(button.isValidSyncHandler(blacklist));
            // Native initialization owns packet transport; verify its two valid states and the real reader here.
            assertDoesNotThrow(() -> button.setState(1, false));
            send(blacklist, true);
            assertTrue(filter.isBlacklistFilter());
            assertDoesNotThrow(() -> button.setState(0, false));
            send(blacklist, false);
            assertFalse(filter.isBlacklistFilter());
            assertThrows(IndexOutOfBoundsException.class, () -> button.setState(2, false));
        }
    }

    @Test
    void hostedItemAndFluidTreesRetainNamedReadOnlyHandlersDuringCollection() {
        for (FilterType type : FilterType.values()) {
            CompositeFilter filter = editable(type);
            PanelSyncManager manager = new PanelSyncManager(false);
            ModularPanel panel = filter.createPopupPanel(manager, "composite_hosted_registration");
            BooleanSyncValue blacklist = manager.findSyncHandler("composite_blacklist", BooleanSyncValue.class);
            BooleanSyncValue valid = manager.findSyncHandler("composite_valid", BooleanSyncValue.class);
            assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
            assertSame(blacklist, manager.findSyncHandler("composite_blacklist", BooleanSyncValue.class));
            assertSame(valid, manager.findSyncHandler("composite_valid", BooleanSyncValue.class));
            send(blacklist, true);
            assertFalse(filter.isBlacklistFilter());
        }
    }

    @Test
    void sneakRightClickRetainsRealChildrenAndUnrelatedItemData() {
        EntityPlayer player = player();
        ItemStack root = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT))).getContainerStack();
        root.getTagCompound().setString("OtherData", "retained");
        NBTTagCompound before = root.writeToNBT(new NBTTagCompound());
        player.setHeldItem(EnumHand.MAIN_HAND, root);
        player.setSneaking(true);
        new CompositeFilterUIManager(ITEM).onItemRightClick(player.world, player, EnumHand.MAIN_HAND);
        assertEquals(before, root.writeToNBT(new NBTTagCompound()));
        assertEquals(1, MetaItems.COMPOSITE_ITEM_FILTER.getStackForm().getMaxStackSize());
        assertEquals(1, MetaItems.COMPOSITE_FLUID_FILTER.getStackForm().getMaxStackSize());
    }

    private static void send(BooleanSyncValue value, boolean blacklist) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeBoolean(blacklist);
            value.read(packet);
        } finally {
            packet.release();
        }
    }

    private static EntityPlayer player() {
        return new EntityPlayer(new DummyWorld(), new GameProfile(UUID.randomUUID(), "composite_test")) {

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
}

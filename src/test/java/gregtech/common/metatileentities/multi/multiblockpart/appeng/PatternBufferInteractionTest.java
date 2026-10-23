package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.RecipeInputView;
import gregtech.api.gui.INativeWidget;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.Widget;
import gregtech.api.gui.widgets.ClickButtonWidget;
import gregtech.api.gui.widgets.TabGroup;
import gregtech.api.gui.widgets.WidgetUIAccess;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferStorage;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTextWidget;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferUI;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferInteractionTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void proxiesShareOneAuthoritativeKeyAndRejectChangedObservationsBeforeTheFirstPollingTick() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = buffer(world);
        EntityPlayer owner = player(world, buffer.owner);
        ItemStack stick = new ItemStack(Items.PAPER);
        buffer.onDataStickLeftClick(owner, stick);
        PatternBufferTestProxy first = new PatternBufferTestProxy(buffer.owner),
                second = new PatternBufferTestProxy(buffer.owner);
        world.put(BlockPos.ORIGIN.east(), first);
        world.put(BlockPos.ORIGIN.west(), second);
        assertTrue(first.onDataStickRightClick(owner, stick));
        assertTrue(second.onDataStickRightClick(owner, stick));
        RecipeInputView one = first.getRecipeInputViews().get(0), two = second.getRecipeInputViews().get(0);
        assertEquals(one.getSelectionKey(), two.getSelectionKey());
        assertEquals(one.getSelectionKey(), buffer.getRecipeInputViews().get(0).getSelectionKey());
        long before = first.getRecipeInputRevision();
        buffer.getPatternStorage().getSharedItems().insertItem(0, new ItemStack(Items.STICK), false);
        assertEquals(before, first.getRecipeInputRevision(), "Pure revision getter never discovers/mutates topology");
        assertFalse(first.isRecipeInputViewValid(one), "Final validity cannot wait for listener polling");
        RecipeInputView refreshed = first.getRecipeInputViews().get(0);
        assertEquals(one.getSelectionKey(), refreshed.getSelectionKey(),
                "Content changes preserve authoritative batch identity");
        assertTrue(first.isRecipeInputViewValid(refreshed));
        assertEquals(3, refreshed.getItems().extractItem(0, 3, false).getCount());
        assertTrue(two.getItems().getStackInSlot(0).isEmpty());
        first.onRecipeInputViewStarted(refreshed);
        assertFalse(second.isRecipeInputViewValid(two));
        assertTrue(buffer.getPatternStorage().getBatches().isEmpty());
        assertEquals(1, buffer.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
    }

    @Test
    void unownedDataStickLinkingReportsUnavailableWithoutThrowingOrChangingEitherPayload() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = buffer(world);
        EntityPlayer player = player(world, UUID.randomUUID());
        buffer.owner = null;
        NBTTagCompound before = buffer.getPatternStorage().write();
        ItemStack stick = new ItemStack(Items.PAPER);
        stick.setTagCompound(new NBTTagCompound());
        stick.getTagCompound().setString("OtherData", "retained");
        NBTTagCompound tag = stick.getTagCompound().copy();
        assertDoesNotThrow(() -> buffer.onDataStickLeftClick(player, stick));
        assertEquals(tag, stick.getTagCompound());
        assertEquals(before, buffer.getPatternStorage().write());
    }

    @Test
    void linkAndUnlinkActionsRequireBothOwnersAndNeverCopyOrRemoveMaterials() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = buffer(world);
        EntityPlayer owner = player(world, buffer.owner), stranger = player(world, UUID.randomUUID());
        ItemStack stick = new ItemStack(Items.PAPER);
        buffer.onDataStickLeftClick(stranger, stick);
        assertFalse(stick.hasTagCompound());
        buffer.onDataStickLeftClick(owner, stick);
        NBTTagCompound source = buffer.getPatternStorage().write();
        PatternBufferTestProxy proxy = new PatternBufferTestProxy(buffer.owner);
        world.put(BlockPos.ORIGIN.east(), proxy);
        assertTrue(proxy.onDataStickRightClick(stranger, stick));
        assertTrue(proxy.getRecipeInputViews().isEmpty());
        assertTrue(proxy.onDataStickRightClick(owner, stick));
        assertEquals(1, proxy.getRecipeInputViews().size());
        proxy.unlink(stranger);
        assertEquals(1, proxy.getRecipeInputViews().size());
        proxy.unlink(owner);
        assertTrue(proxy.getRecipeInputViews().isEmpty());
        assertEquals(source, buffer.getPatternStorage().write());
        world.modifiable = false;
        assertFalse(buffer.canAccess(owner));
        assertTrue(proxy.onDataStickRightClick(owner, stick));
        assertTrue(proxy.getRecipeInputViews().isEmpty());
    }

    @Test
    void staleProxyActionsRejectUnloadedRemovedAndReplacedHoldersWithoutChangingItsLink() {
        for (int state = 0; state < 3; state++) {
            PatternBufferTestWorld world = new PatternBufferTestWorld();
            PatternBufferTestPart buffer = buffer(world);
            EntityPlayer owner = player(world, buffer.owner);
            ItemStack stick = new ItemStack(Items.PAPER);
            buffer.onDataStickLeftClick(owner, stick);
            PatternBufferTestProxy proxy = new PatternBufferTestProxy(buffer.owner);
            world.put(BlockPos.ORIGIN.east(), proxy);
            assertTrue(proxy.onDataStickRightClick(owner, stick));
            NBTTagCompound before = new NBTTagCompound();
            proxy.writeItemStackData(before);
            if (state == 0) world.loaded = false;
            else if (state == 1) proxy.onRemoval();
            else world.put(BlockPos.ORIGIN.east(), new PatternBufferTestProxy(buffer.owner));
            assertFalse(proxy.canAccess(owner));
            proxy.unlink(owner);
            assertTrue(proxy.onDataStickRightClick(owner, stick));
            NBTTagCompound after = new NBTTagCompound();
            proxy.writeItemStackData(after);
            assertEquals(before, after);
        }
    }

    @Test
    void actualButtonPacketsAuthorizeRefundAndUseStableBatchIdAfterListChanges() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = buffer(world);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertTrue(buffer.getPatternStorage().accept(0, details(iron), table(iron), false));
        EntityPlayer owner = player(world, buffer.owner), stranger = player(world, UUID.randomUUID());
        ModularUI denied = PatternBufferUI.create(buffer, stranger);
        click(button(denied, 7, 156));
        click(button(denied, 87, 156));
        assertTrue(buffer.isWorkingEnabled());
        assertFalse(buffer.getPatternStorage().getBatches().get(0).isRefundPending());
        ModularUI ui = PatternBufferUI.create(buffer, owner);
        TabGroup<?> tabs = (TabGroup<?>) ui.guiWidgets.values().stream().filter(TabGroup.class::isInstance).findFirst()
                .get();
        ClickButtonWidget selectedRefund = (ClickButtonWidget) tabs.getTabWidget(2).getContainedWidgets(true).stream()
                .filter(widget -> widget instanceof ClickButtonWidget && widget.getSelfPosition().y == 99).findFirst()
                .get();
        buffer.getPatternStorage().getBatches().get(0).getItems().extractItem(0, 3, false);
        buffer.getPatternStorage().pruneEmpty();
        click(selectedRefund);
        assertFalse(buffer.getPatternStorage().getBatches().get(0).isRefundPending(),
                "A vanished selected batch must not refund a different batch shifted into its former index");
        click(button(ui, 87, 156));
        assertTrue(buffer.getPatternStorage().getBatches().get(0).isRefundPending());
        click(button(ui, 7, 156));
        assertFalse(buffer.isWorkingEnabled());
    }

    @Test
    void statusTransportIsOneBoundedChangedRowAndNeverSerializesPrivateNbt() {
        AtomicReference<String> source = new AtomicReference<>("initial");
        List<String> packets = new ArrayList<>();
        PatternBufferTextWidget widget = new PatternBufferTextWidget(0, 0, source::get);
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        widget.setGui(PatternBufferUI.create(part, player(world, part.owner)));
        widget.setUiAccess(new WidgetUIAccess() {

            @Override
            public void notifySizeChange() {}

            @Override
            public void notifyWidgetChange() {}

            @Override
            public boolean attemptMergeStack(ItemStack stack, boolean from, boolean simulate) {
                return false;
            }

            @Override
            public void sendSlotUpdate(INativeWidget slot) {}

            @Override
            public void sendHeldItemUpdate() {}

            @Override
            public void writeClientAction(Widget target, int id, Consumer<PacketBuffer> writer) {
                fail("Display only");
            }

            @Override
            public void writeUpdateInfo(Widget target, int id, Consumer<PacketBuffer> writer) {
                PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
                try {
                    writer.accept(packet);
                    assertTrue(packet.readableBytes() <= 402);
                    String text = packet.readString(100);
                    assertTrue(text.length() <= 100);
                    assertEquals(0, packet.readableBytes());
                    packets.add(text);
                } finally {
                    packet.release();
                }
            }
        });
        widget.detectAndSendChanges();
        for (int i = 0; i < 40; i++) widget.detectAndSendChanges();
        assertEquals(1, packets.size());
        source.set(new String(new char[10_000]).replace('\0', 'X'));
        widget.detectAndSendChanges();
        assertEquals(100, packets.get(1).length());
        assertTrue(packets.get(1).endsWith("..."));
        source.set("");
        widget.detectAndSendChanges();
        assertEquals("", packets.get(2));
    }

    private static PatternBufferTestPart buffer(PatternBufferTestWorld world) {
        PatternBufferStorage ledger = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertTrue(ledger.accept(0, details(iron), table(iron), false));
        PatternBufferTestPart buffer = new PatternBufferTestPart();
        world.put(BlockPos.ORIGIN, buffer);
        buffer.getPatternStorage().read(ledger.write());
        return buffer;
    }

    private static EntityPlayer player(PatternBufferTestWorld world, UUID id) {
        return new EntityPlayer(world, new GameProfile(id, "pattern_buffer_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean canUseCommand(int level, String command) {
                return false;
            }

            @Override
            public void sendStatusMessage(ITextComponent text, boolean actionBar) {}
        };
    }

    private static ClickButtonWidget button(ModularUI ui, int x, int y) {
        return (ClickButtonWidget) ui.guiWidgets.values().stream()
                .filter(widget -> widget instanceof ClickButtonWidget &&
                        widget.getSelfPosition().x == x && widget.getSelfPosition().y == y)
                .findFirst().get();
    }

    private static void click(ClickButtonWidget button) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new Widget.ClickData(0, false, false).writeToBuf(packet);
            button.handleClientAction(1, packet);
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }
}

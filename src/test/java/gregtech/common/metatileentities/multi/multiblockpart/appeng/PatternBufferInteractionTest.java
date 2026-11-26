package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.RecipeInputView;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferStorage;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferUI;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
    void actualButtonPacketsAuthorizeRefundAndUseStableBatchIdAfterListChanges() throws Exception {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = buffer(world);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertTrue(buffer.getPatternStorage().accept(0, details(iron), table(iron), false));
        EntityPlayer owner = player(world, buffer.owner), stranger = player(world, UUID.randomUUID());
        PatternBufferScreen denied = PatternBufferScreen.open(buffer, stranger);
        denied.press("pattern_buffer_toggle");
        denied.press("pattern_buffer_refund_all");
        assertTrue(buffer.isWorkingEnabled());
        assertFalse(buffer.getPatternStorage().getBatches().get(0).isRefundPending());
        PatternBufferScreen screen = PatternBufferScreen.open(buffer, owner);
        buffer.getPatternStorage().getBatches().get(0).getItems().extractItem(0, 3, false);
        buffer.getPatternStorage().pruneEmpty();
        screen.press("pattern_buffer_refund_batch");
        assertFalse(buffer.getPatternStorage().getBatches().get(0).isRefundPending(),
                "A vanished selected batch must not refund a different batch shifted into its former index");
        screen.press("pattern_buffer_refund_all");
        assertTrue(buffer.getPatternStorage().getBatches().get(0).isRefundPending());
        screen.press("pattern_buffer_toggle");
        assertFalse(buffer.isWorkingEnabled());
    }

    @Test
    void statusTransportIsOneBoundedChangedRowAndNeverSerializesPrivateNbt() throws Exception {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(false), true);
        part.buildUI(null, manager, new UISettings());
        StringSyncValue status = manager.findSyncHandler("pattern_buffer_status", StringSyncValue.class);
        assertTrue(status.updateCacheFromSource(true));
        for (int i = 0; i < 40; i++) assertFalse(status.updateCacheFromSource(false), "an unchanged row is not sent");
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            status.write(packet);
            assertTrue(packet.readableBytes() <= 402);
        } finally {
            packet.release();
        }
        String overlong = new String(new char[10_000]).replace('\0', 'X');
        assertEquals(100, PatternBufferUI.bounded(overlong).length());
        assertTrue(PatternBufferUI.bounded(overlong).endsWith("..."));
        assertEquals("", PatternBufferUI.bounded(""));
        for (int row = 0; row < 6; row++) {
            assertNotNull(manager.findSyncHandlerNullable("pattern_buffer_entry_" + row));
        }
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
}

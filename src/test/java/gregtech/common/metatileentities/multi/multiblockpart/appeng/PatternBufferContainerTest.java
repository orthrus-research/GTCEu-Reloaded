package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferStorage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferContainerTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void actualSlotPredicatesAndLimitsArePureAndKeepTheSameBackingStack() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        EntityPlayer player = player(world, part);
        part.getPatternStorage().getSharedItems().insertItem(0, new ItemStack(Items.IRON_INGOT, 32), false);
        ModularContainer container = container(part, player);
        Slot slot = sharedSlot(container);
        ItemStack backing = slot.getStack();
        NBTTagCompound before = part.getPatternStorage().write();
        long revision = part.getRecipeInputRevision();
        part.controller.getNotifiedItemInputList().clear();
        for (int i = 0; i < 40; i++) {
            assertTrue(slot.isItemValid(new ItemStack(Items.IRON_INGOT)));
            assertEquals(64, slot.getItemStackLimit(new ItemStack(Items.IRON_INGOT)));
            assertSame(backing, slot.getStack());
        }
        Slot locked = patternSlot(container);
        ItemStack pattern = locked.getStack();
        assertFalse(locked.isItemValid(pattern));
        assertEquals(0, locked.getItemStackLimit(pattern));
        assertSame(pattern, locked.getStack());
        assertEquals(before, part.getPatternStorage().write());
        assertEquals(revision, part.getRecipeInputRevision());
        assertTrue(part.controller.getNotifiedItemInputList().isEmpty());
    }

    @Test
    void partialShiftOutWithOnePlayerSpaceConservesActualBackingStackAndNotifies() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        EntityPlayer player = player(world, part);
        part.getPatternStorage().getSharedItems().insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false);
        for (int i = 0; i < 36; i++) player.inventory.setInventorySlotContents(i, new ItemStack(Items.DIAMOND, 64));
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT, 63));
        ModularContainer container = container(part, player);
        long revision = part.getRecipeInputRevision();
        part.controller.getNotifiedItemInputList().clear();
        container.slotClick(sharedSlot(container).slotNumber, 0, ClickType.QUICK_MOVE, player);
        assertEquals(63, part.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
        assertEquals(64, player.inventory.getStackInSlot(0).getCount());
        assertEquals(127, ironTotal(part, player));
        assertTrue(part.getRecipeInputRevision() > revision);
        assertFalse(part.controller.getNotifiedItemInputList().isEmpty());
    }

    @Test
    void partialShiftIntoExistingSharedStackConservesRemainderAndNotifies() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        EntityPlayer player = player(world, part);
        part.getPatternStorage().getSharedItems().insertItem(0, new ItemStack(Items.IRON_INGOT, 63), false);
        for (int i = 1; i < 9; i++)
            part.getPatternStorage().getSharedItems().insertItem(i, new ItemStack(Items.DIAMOND, 64), false);
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT, 2));
        ModularContainer container = container(part, player);
        long revision = part.getRecipeInputRevision();
        part.controller.getNotifiedItemInputList().clear();
        ItemStack held = player.inventory.getStackInSlot(0);
        Slot source = container.inventorySlots.stream().filter(slot -> slot.getStack() == held).findFirst().get();
        container.slotClick(source.slotNumber, 0, ClickType.QUICK_MOVE, player);
        assertEquals(64, part.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
        assertEquals(1, player.inventory.getStackInSlot(0).getCount());
        assertEquals(65, ironTotal(part, player));
        assertTrue(part.getRecipeInputRevision() > revision);
        assertFalse(part.controller.getNotifiedItemInputList().isEmpty());
    }

    @Test
    void actualCursorMergeAndHalfPickupKeepExactCountsAndLedgerSnapshotsOwned() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        EntityPlayer player = player(world, part);
        part.getPatternStorage().getSharedItems().insertItem(0, new ItemStack(Items.IRON_INGOT, 60), false);
        player.inventory.setItemStack(new ItemStack(Items.IRON_INGOT, 10));
        ModularContainer container = container(part, player);
        int target = sharedSlot(container).slotNumber;
        long revision = part.getRecipeInputRevision();
        container.slotClick(target, 0, ClickType.PICKUP, player);
        assertEquals(64, part.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
        assertEquals(6, player.inventory.getItemStack().getCount());
        assertEquals(70, ironTotal(part, player));
        assertTrue(part.getRecipeInputRevision() > revision);
        player.inventory.setInventorySlotContents(0, player.inventory.getItemStack());
        player.inventory.setItemStack(ItemStack.EMPTY);
        revision = part.getRecipeInputRevision();
        container.slotClick(target, 1, ClickType.PICKUP, player);
        assertEquals(32, part.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
        assertEquals(32, player.inventory.getItemStack().getCount());
        assertEquals(70, ironTotal(part, player));
        assertTrue(part.getRecipeInputRevision() > revision);
        ItemStack external = part.getPatternStorage().getSharedItems().getStackInSlot(0);
        external.setCount(1);
        assertEquals(32, part.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
        ItemStack privateCopy = part.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0);
        privateCopy.setCount(1);
        assertEquals(3, part.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0).getCount());
    }

    @Test
    void nativeSlotGesturesCannotExtractLockedPatternsOrEditQuarantine() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart part = buffer(world);
        EntityPlayer player = player(world, part);
        ModularContainer container = container(part, player);
        Slot pattern = patternSlot(container);
        NBTTagCompound before = part.getPatternStorage().write();
        assertTrue(container.slotClick(pattern.slotNumber, 0, ClickType.QUICK_MOVE, player).isEmpty());
        container.slotClick(pattern.slotNumber, 0, ClickType.PICKUP, player);
        assertTrue(player.inventory.getItemStack().isEmpty());
        for (ItemStack stack : player.inventory.mainInventory) assertTrue(stack.isEmpty(), "no pattern was copied");
        assertEquals(before, part.getPatternStorage().write());
        before.setInteger("Version", 999);
        part.getPatternStorage().read(before);
        player.inventory.setItemStack(new ItemStack(Items.IRON_INGOT, 2));
        container.slotClick(sharedSlot(container).slotNumber, 0, ClickType.PICKUP, player);
        assertEquals(2, player.inventory.getItemStack().getCount());
        assertEquals(before, part.getPatternStorage().write());
    }

    @Test
    void lateActualTankPacketsAfterRemovalOrSamePositionReplacementCannotDuplicateRecoveryFluids() {
        for (boolean removal : new boolean[] { false, true }) {
            PatternBufferTestWorld world = new PatternBufferTestWorld();
            PatternBufferTestPart part = buffer(world);
            EntityPlayer player = player(world, part);
            part.getPatternStorage().getSharedFluids().fill(new FluidStack(FluidRegistry.WATER, 1000), true);
            player.inventory.setItemStack(new ItemStack(Items.BUCKET));
            assertNotNull(player.inventory.getItemStack()
                    .getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null));
            PatternBufferScreen screen = PatternBufferScreen.open(part, player);
            GTFluidSyncHandler tank = (GTFluidSyncHandler) screen.syncManager.findSyncHandler("pattern_buffer_tank",
                    0);
            NBTTagCompound recovery = new NBTTagCompound();
            part.writeItemStackData(recovery);
            NBTTagCompound recoveryBefore = recovery.copy(), original = part.getPatternStorage().write();
            if (removal) part.onRemoval();
            PatternBufferTestPart replacement = new PatternBufferTestPart();
            world.put(BlockPos.ORIGIN, replacement);
            replacement.getPatternStorage().getSharedFluids().fill(new FluidStack(FluidRegistry.LAVA, 500), true);
            NBTTagCompound replacementBefore = replacement.getPatternStorage().write();
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                packet.writeBoolean(false);
                try {
                    tank.readOnServer(GTFluidSyncHandler.TRY_CLICK_CONTAINER, packet);
                } finally {
                    assertEquals(1, packet.readableBytes(),
                            "Invalid old screen is rejected before parsing its tank action");
                }
            } finally {
                packet.release();
            }
            assertFalse(part.canAccess(player));
            assertEquals(Items.BUCKET, player.inventory.getItemStack().getItem());
            assertEquals(1, player.inventory.getItemStack().getCount());
            assertEquals(original, part.getPatternStorage().write());
            assertEquals(recoveryBefore, recovery);
            assertEquals(replacementBefore, replacement.getPatternStorage().write());
        }
    }

    private static PatternBufferTestPart buffer(PatternBufferTestWorld world) {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertTrue(storage.accept(0, details(iron), table(iron), false));
        PatternBufferTestPart part = new PatternBufferTestPart();
        world.put(BlockPos.ORIGIN, part);
        part.getPatternStorage().read(storage.write());
        return part;
    }

    private static EntityPlayer player(PatternBufferTestWorld world, PatternBufferTestPart part) {
        return new EntityPlayer(world, new GameProfile(part.owner, "pattern_container_test")) {

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

    private static ModularContainer container(PatternBufferTestPart part, EntityPlayer player) {
        return PatternBufferScreen.open(part, player).container;
    }

    private static Slot slotOf(ModularContainer container, String group) {
        return container.inventorySlots.stream()
                .filter(slot -> slot instanceof ModularSlot modular && group.equals(modular.getSlotGroupName()) &&
                        slot.getSlotIndex() == 0)
                .findFirst().get();
    }

    private static Slot patternSlot(ModularContainer container) {
        return slotOf(container, "pattern_buffer_patterns");
    }

    private static Slot sharedSlot(ModularContainer container) {
        return slotOf(container, "pattern_buffer_shared");
    }

    private static int ironTotal(PatternBufferTestPart part, EntityPlayer player) {
        int total = part.getPatternStorage().getSharedItems().getStackInSlot(0).getCount();
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack.getItem() == Items.IRON_INGOT) total += stack.getCount();
        if (player.inventory.getItemStack().getItem() == Items.IRON_INGOT)
            total += player.inventory.getItemStack().getCount();
        return total;
    }
}

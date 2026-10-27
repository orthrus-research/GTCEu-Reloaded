package gregtech.api.configuration;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.FacingPos;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverItemFilter;
import gregtech.common.covers.CoverRoboticArm;
import gregtech.common.covers.ItemFilterMode;
import gregtech.common.pipelike.itempipe.BlockItemPipe;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.ItemNetHandler;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipe;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemPipePromotionRegressionTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void actualPromotionKeepsCoverWrapperIdentityAndSubsequentPolicyEdits() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        BlockItemPipe block = new BlockItemPipe(ItemPipeType.NORMAL, Materials.Tin.getRegistry());
        block.addPipeMaterial(Materials.Tin, new ItemPipeProperties(4096, 0.5f));
        TileEntityItemPipe original = new TileEntityItemPipe();
        world.put(ItemPipeTransportFixture.POS, original, block.getDefaultState());
        original.setPipeData(block, ItemPipeType.NORMAL, Materials.Tin);
        WorldItemPipeNet.getWorldPipeNet(world).addNode(original.getPos(), original.getNodeData(), 0, 0, false);
        ItemStackHandler destination = new ItemStackHandler(1);
        world.put(original.getPos().east(), new ItemPipeTransportFixture.Inventory(destination),
                Blocks.CHEST.getDefaultState());
        original.setConnection(EnumFacing.WEST, true, false);
        original.setConnection(EnumFacing.EAST, true, false);
        original.setFaceBlocked(EnumFacing.UP, true);
        original.addTransferredItems(7);
        CoverDefinition filterDefinition = new CoverDefinition(GTUtility.gregtechId("promotion_filter"),
                (def, holder, side) -> new CoverItemFilter(def, holder, side, "test", null), ItemStack.EMPTY);
        CoverItemFilter filter = (CoverItemFilter) filterDefinition.createCover(original.getCoverableImplementation(),
                EnumFacing.WEST);
        original.getCoverableImplementation().addCover(EnumFacing.WEST, filter);
        IItemHandler held = original.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.WEST);
        assertNotNull(held);
        assertTrue(held.insertItem(0, new ItemStack(Blocks.COBBLESTONE), true).isEmpty());

        TileEntityItemPipe promoted = (TileEntityItemPipe) original.setSupportsTicking();

        assertNotSame(original, promoted);
        assertSame(promoted, world.getTileEntity(original.getPos()));
        assertSame(filter, promoted.getCoverableImplementation().getCoverAtSide(EnumFacing.WEST));
        assertSame(held, promoted.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.WEST));
        assertTrue(promoted.isFaceBlocked(EnumFacing.UP));
        assertFalse(promoted.isPainted());
        assertEquals(7, promoted.getTransferredItems());
        filter.setFilterMode(ItemFilterMode.FILTER_EXTRACT);
        assertEquals(1, held.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).getCount());
        filter.setFilterMode(ItemFilterMode.FILTER_INSERT);
        assertTrue(held.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).isEmpty());
        assertEquals(1, destination.getStackInSlot(0).getCount());
    }

    @Test
    void deliberateCompatibleCoverCopyRetainsExactArmRuntimeCredit() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(64);
        fixture.arm.buffer(13);
        CoverDefinition definition = fixture.arm.getDefinition();
        CoverRoboticArm copy = (CoverRoboticArm) definition.createCover(fixture.pipe.getCoverableImplementation(),
                EnumFacing.EAST);
        copy.copyRuntimeStateFrom(fixture.arm);
        assertEquals(13, copy.getBuffer());
    }

    @Test
    void callbackPromotionSharesGuardUntilSuccessfulInsertionCompletes() {
        for (boolean externalHandler : new boolean[] { true, false }) promotionDuringTransfer(externalHandler, false);
    }

    @Test
    void callbackPromotionSharesGuardUntilThrowingInsertionUnwinds() {
        for (boolean externalHandler : new boolean[] { true, false }) promotionDuringTransfer(externalHandler, true);
    }

    private static void promotionDuringTransfer(boolean externalHandler, boolean throwAfterPromotion) {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        BlockItemPipe block = new BlockItemPipe(ItemPipeType.NORMAL, Materials.Tin.getRegistry());
        block.addPipeMaterial(Materials.Tin, new ItemPipeProperties(4096, 0.5f));
        TileEntityItemPipe original = new TileEntityItemPipe();
        world.put(ItemPipeTransportFixture.POS, original, block.getDefaultState());
        original.setPipeData(block, ItemPipeType.NORMAL, Materials.Tin);
        WorldItemPipeNet.getWorldPipeNet(world).addNode(original.getPos(), original.getNodeData(), 0, 0, false);
        original.setConnection(EnumFacing.WEST, true, false);
        original.setConnection(EnumFacing.NORTH, true, false);
        original.setConnection(EnumFacing.EAST, true, false);
        TileEntityItemPipe[] replacement = { null };
        int[] nestedRemainder = { -1 };
        ItemStackHandler destination = new ItemStackHandler(1) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!simulate && replacement[0] == null) {
                    replacement[0] = (TileEntityItemPipe) original.setSupportsTicking();
                    IItemHandler otherFace = replacement[0].getCapability(
                            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
                    assertNotNull(otherFace);
                    nestedRemainder[0] = otherFace.insertItem(0, new ItemStack(Blocks.DIRT), false).getCount();
                    if (throwAfterPromotion) throw new IllegalStateException("promotion endpoint failure");
                }
                return super.insertItem(slot, stack, simulate);
            }
        };
        world.put(original.getPos().east(), new ItemPipeTransportFixture.Inventory(destination),
                Blocks.CHEST.getDefaultState());
        IItemHandler retained = externalHandler ?
                new ItemNetHandler(original.getItemPipeNet(), original, EnumFacing.WEST) :
                original.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.WEST);
        assertNotNull(retained);
        assertEquals(32, original.getAvailableExactTransferItems(32, 64));
        ItemStack offered = new ItemStack(Blocks.COBBLESTONE, 8);
        if (throwAfterPromotion) {
            assertThrows(IllegalStateException.class, () -> retained.insertItem(0, offered, false));
            assertTrue(destination.getStackInSlot(0).isEmpty());
        } else {
            assertTrue(retained.insertItem(0, offered, false).isEmpty());
            assertEquals(8, destination.getStackInSlot(0).getCount());
        }
        assertNotNull(replacement[0]);
        assertNotSame(original, replacement[0]);
        assertSame(replacement[0], world.getTileEntity(original.getPos()));
        assertEquals(1, nestedRemainder[0], "Promotion must not admit a second face during the active operation");
        int accepted = throwAfterPromotion ? 0 : 8;
        assertEquals(accepted, replacement[0].getTransferredItems(), "The replacement must own in-flight charges");
        assertEquals(accepted, original.getTransferredItems());
        assertEquals(32 - accepted, original.getAvailableExactTransferItems(32, 64));
        assertEquals(32 - accepted, replacement[0].getAvailableExactTransferItems(32, 64));
        assertSame(original.getTransferred(), replacement[0].getTransferred(), "RR accounting must follow the handoff");
        FacingPos marker = new FacingPos(original.getPos().east(), EnumFacing.EAST);
        original.getTransferred().put(marker, 3);
        assertEquals(3, replacement[0].getTransferred().getInt(marker));
        replacement[0].getTransferred().put(marker, 4);
        assertEquals(4, original.getTransferred().getInt(marker));
        original.resetTransferred();
        assertTrue(replacement[0].getTransferred().isEmpty());
        IItemHandler fresh = replacement[0].getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                EnumFacing.WEST);
        assertNotNull(fresh);
        assertEquals(accepted, fresh.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 32), false).getCount(),
                "Old and fresh handlers must share the source's remaining 32-item window");
        assertEquals(32, destination.getStackInSlot(0).getCount());
        assertEquals(32, original.getTransferredItems());
        assertEquals(32, replacement[0].getTransferredItems());
        assertEquals(0, original.getAvailableExactTransferItems(32, 64));
        assertEquals(1, retained.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).getCount());
        world.setTotalWorldTime(20);
        assertTrue(retained.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false).isEmpty(),
                "Promotion must not leave the replacement ingress guard latched");
        assertEquals(33, destination.getStackInSlot(0).getCount());
        assertEquals(1, original.getTransferredItems());
        assertEquals(1, replacement[0].getTransferredItems());
        assertTrue(original.tryBeginItemTransfer(), "The original owner's unwind must also release its guard");
        assertFalse(replacement[0].tryBeginItemTransfer(), "Both owners must share the active guard after handoff");
        original.endItemTransfer();
        assertTrue(replacement[0].tryBeginItemTransfer());
        replacement[0].endItemTransfer();
    }

    @Test
    void genericTransferCopyKeepsCountersRoundRobinAndExactBudgetsIndependent() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        assertEquals(32, fixture.pipe.getAvailableExactTransferItems(32, 64));
        fixture.pipe.addTransferredItems(5);
        TileEntityItemPipe copy = new TileEntityItemPipe();
        copy.setWorld(fixture.world);
        copy.setPos(ItemPipeTransportFixture.POS.east(8));
        copy.transferDataFrom(fixture.pipe);

        assertEquals(27, fixture.pipe.getAvailableExactTransferItems(32, 64));
        assertEquals(27, copy.getAvailableExactTransferItems(32, 64));

        fixture.pipe.addTransferredItems(3);
        assertEquals(24, fixture.pipe.getAvailableExactTransferItems(32, 64));
        assertEquals(27, copy.getAvailableExactTransferItems(32, 64));
        copy.addTransferredItems(2);

        assertEquals(8, fixture.pipe.getTransferredItems());
        assertEquals(7, copy.getTransferredItems());
        assertNotSame(fixture.pipe.getTransferred(), copy.getTransferred());
        assertEquals(24, fixture.pipe.getAvailableExactTransferItems(32, 64));
        assertEquals(25, copy.getAvailableExactTransferItems(32, 64));

        fixture.time(20);
        assertEquals(56, fixture.pipe.getAvailableExactTransferItems(32, 64));
        assertEquals(57, copy.getAvailableExactTransferItems(32, 64));
    }
}

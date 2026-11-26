package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.MEStockingConfigTest.settings;
import static org.junit.jupiter.api.Assertions.*;

class MEAbundantStockingTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void deniedAndBelowMinimumEntriesAheadOfTheCutoffDoNotDisplaceValidStocks() {
        StockingTestBus bus = bus();
        StockingTestHatch hatch = hatch();
        bus.storage.contents.add(item(99, 9));
        hatch.storage.contents.add(fluid(99, 9));
        for (int id = 0; id < 40; id++) {
            bus.storage.contents.add(item(id, 100 + id));
            hatch.storage.contents.add(fluid(id, 100 + id));
        }
        bus.storage.extractionRule = stack -> itemId(stack) < 20 ? itemId(stack) % 2 * 9 : Long.MAX_VALUE;
        hatch.storage.extractionRule = stack -> fluidId(stack) < 20 ? fluidId(stack) % 2 * 9 : Long.MAX_VALUE;
        bus.storage.mutateRequests = true;
        hatch.storage.mutateRequests = true;
        bus.advance(1);
        hatch.advance(1);
        List<Integer> expected = IntStream.range(24, 40).boxed().sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        assertEquals(expected, itemIds(bus));
        assertEquals(expected, fluidIds(hatch));
        assertEquals(56, bus.storage.simulations);
        assertEquals(56, hatch.storage.simulations);
        assertEquals(1, bus.storage.listScans);
        assertEquals(1, hatch.storage.listScans);
        assertEquals(0, bus.storage.modulations);
        assertEquals(0, hatch.storage.modulations);
        for (int i = 1; i < bus.storage.contents.size(); i++) {
            assertEquals(99 + i, bus.storage.contents.get(i).getStackSize());
            assertEquals(99 + i, hatch.storage.contents.get(i).getStackSize());
        }
    }

    @Test
    void richestSixteenAreIndependentOfAscendingDescendingOrShuffledNetworkOrder() {
        StockingTestBus bus = bus();
        StockingTestHatch hatch = hatch();
        for (int id = 0; id < 40; id++) {
            bus.storage.contents.add(item(id, id + 10));
            hatch.storage.contents.add(fluid(id, id + 10));
        }
        List<Integer> expected = IntStream.range(24, 40).boxed().sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        for (int seed = 0; seed < 12; seed++) {
            bus.advance(1);
            hatch.advance(1);
            assertEquals(expected, itemIds(bus));
            assertEquals(expected, fluidIds(hatch));
            if (seed == 0) {
                Collections.reverse(bus.storage.contents);
                Collections.reverse(hatch.storage.contents);
            } else {
                Collections.shuffle(bus.storage.contents, new Random(seed));
                Collections.shuffle(hatch.storage.contents, new Random(seed));
            }
        }
    }

    @Test
    void fullLongQuantitiesDetermineOrderButOnlyDisplayCopiesAreCapped() {
        StockingTestBus bus = bus();
        StockingTestHatch hatch = hatch();
        long[] amounts = { Integer.MAX_VALUE - 1L, Integer.MAX_VALUE + 1L, 1L << 40, Long.MAX_VALUE - 1 };
        for (int id = 0; id < amounts.length; id++) {
            bus.storage.contents.add(item(id, amounts[id]));
            hatch.storage.contents.add(fluid(id, amounts[id]));
        }
        bus.storage.mutateRequests = true;
        hatch.storage.mutateRequests = true;
        bus.advance(1);
        hatch.advance(1);
        assertEquals(Arrays.asList(3, 2, 1, 0), itemIds(bus));
        assertEquals(Arrays.asList(3, 2, 1, 0), fluidIds(hatch));
        for (int slot = 0; slot < amounts.length; slot++) {
            long visible = Math.min(Integer.MAX_VALUE, amounts[3 - slot]);
            assertEquals(visible, bus.slots()[slot].getStock().getStackSize());
            assertEquals(visible, hatch.slots()[slot].getStock().getStackSize());
            assertEquals(1, bus.slots()[slot].getConfig().getStackSize());
            assertEquals(1, hatch.slots()[slot].getConfig().getStackSize());
            assertEquals(amounts[slot], bus.storage.contents.get(slot).getStackSize());
            assertEquals(amounts[slot], hatch.storage.contents.get(slot).getStackSize());
        }
        assertEquals(MEStockingIdentity.item(item(0, 1)), MEStockingIdentity.item(item(0, Long.MAX_VALUE)));
        assertEquals(MEStockingIdentity.fluid(fluid(0, 1)), MEStockingIdentity.fluid(fluid(0, Long.MAX_VALUE)));
        assertEquals(0, bus.storage.modulations);
        assertEquals(0, hatch.storage.modulations);
    }

    @Test
    void abundantNativeItemStacksReachTheScreenWithTheirFullAmounts() {
        StockingTestBus bus = bus();
        for (int id = 0; id < 16; id++) bus.storage.contents.add(item(id, 64 + id));
        bus.advance(1);
        assertInstanceOf(AEItemStack.class, bus.storage.contents.get(0));
        MEConfigSyncHandler<IAEItemStack, ItemStack> server = bus.createConfigSync();
        MEConfigSyncHandler<IAEItemStack, ItemStack> client = bus.createConfigSync();
        sync(server, client, true);
        assertTrue(client.isAutoPull());
        for (int index = 0; index < 16; index++) {
            var expected = bus.slots()[index];
            assertInstanceOf(WrappedItemStack.class, expected.getConfig());
            assertInstanceOf(WrappedItemStack.class, expected.getStock());
            assertSameItem(expected.getConfig().createItemStack(), client.getConfig(index).createItemStack());
            assertSameItem(expected.getStock().createItemStack(), client.getStock(index).createItemStack());
            assertEquals(1, client.getConfig(index).getStackSize());
            assertEquals(79 - index, client.getStock(index).getStackSize());
        }
        assertFalse(server.collectChanges(false), "an unchanged part sends nothing");
        bus.storage.contents.clear();
        bus.advance(1);
        sync(server, client, false);
        for (int index = 0; index < 16; index++) {
            assertNull(client.getConfig(index));
            assertNull(client.getStock(index));
        }
        assertEquals(0, bus.storage.modulations);
    }

    @Test
    void abundantNativeFluidStacksReachTheScreenWithoutChangingServerTags() {
        StockingTestHatch hatch = hatch();
        for (int id = 0; id < 16; id++) hatch.storage.contents.add(fluid(id, 1000 + id));
        hatch.advance(1);
        assertInstanceOf(AEFluidStack.class, hatch.storage.contents.get(0));
        MEConfigSyncHandler<IAEFluidStack, FluidStack> server = hatch.createConfigSync();
        MEConfigSyncHandler<IAEFluidStack, FluidStack> client = hatch.createConfigSync();
        sync(server, client, true);
        for (int index = 0; index < 16; index++) {
            var expected = hatch.slots()[index];
            assertInstanceOf(WrappedFluidStack.class, expected.getConfig());
            assertInstanceOf(WrappedFluidStack.class, expected.getStock());
            assertEquals(tag(15 - index), expected.getConfig().getFluidStack().tag);
            assertEquals(tag(15 - index), expected.getStock().getFluidStack().tag);
            assertSame(expected.getConfig().getFluid(), client.getConfig(index).getFluid());
            assertSame(expected.getStock().getFluid(), client.getStock(index).getFluid());
            assertEquals(1, client.getConfig(index).getStackSize());
            assertEquals(1015 - index, client.getStock(index).getStackSize());
            // the screen shows a fluid's tag as well
            assertEquals(tag(15 - index), client.getConfig(index).getFluidStack().tag);
        }
        for (int id = 0; id < 16; id++) {
            assertEquals(tag(id), hatch.storage.contents.get(id).getFluidStack().tag);
            assertEquals(1000 + id, hatch.storage.contents.get(id).getStackSize());
        }
        hatch.storage.contents.clear();
        hatch.advance(1);
        sync(server, client, false);
        for (int index = 0; index < 16; index++) {
            assertNull(client.getConfig(index));
            assertNull(client.getStock(index));
        }
        assertEquals(0, hatch.storage.modulations);
    }

    @Test
    void tiedNbtDistinctTypesRemainStableAcrossPermutationsAndReload() {
        StockingTestBus bus = bus();
        StockingTestHatch hatch = hatch();
        for (int id = 0; id < 40; id++) {
            bus.storage.contents.add(item(id, 10));
            hatch.storage.contents.add(fluid(id, 10));
        }
        bus.advance(1);
        hatch.advance(1);
        List<Integer> expectedItems = itemIds(bus);
        List<Integer> expectedFluids = fluidIds(hatch);
        assertEquals(16, expectedItems.size());
        assertEquals(16, expectedFluids.size());
        for (int seed = 0; seed < 12; seed++) {
            Collections.shuffle(bus.storage.contents, new Random(seed));
            Collections.shuffle(hatch.storage.contents, new Random(seed));
            bus.advance(1);
            hatch.advance(1);
            assertEquals(expectedItems, itemIds(bus));
            assertEquals(expectedFluids, fluidIds(hatch));
        }
        StockingTestBus reloadedBus = bus();
        StockingTestHatch reloadedHatch = hatch();
        reloadedBus.readFromNBT(bus.writeToNBT(new NBTTagCompound()));
        reloadedHatch.readFromNBT(hatch.writeToNBT(new NBTTagCompound()));
        reloadedBus.storage.contents.addAll(bus.storage.contents);
        reloadedHatch.storage.contents.addAll(hatch.storage.contents);
        reloadedBus.advance(1);
        reloadedHatch.advance(1);
        assertEquals(expectedItems, itemIds(reloadedBus));
        assertEquals(expectedFluids, fluidIds(reloadedHatch));
    }

    @Test
    void duplicateAndDistinctRulesStillFilterBeforeAbundanceRanking() {
        StockingTestController controller = new StockingTestController();
        StockingTestBus first = bus();
        StockingTestBus second = bus();
        first.controller = controller;
        second.controller = controller;
        controller.inputs.add(first.itemAbility());
        controller.inputs.add(second.itemAbility());
        first.addToMultiBlock(controller);
        second.addToMultiBlock(controller);
        first.slots()[0].setConfig(item(0, 1));
        second.storage.contents.add(item(0, 1000000));
        second.storage.contents.add(item(1, 10));
        second.advance(1);
        assertEquals(Collections.singletonList(1), itemIds(second));
        controller.distinct = true;
        second.advance(1);
        assertEquals(Arrays.asList(0, 1), itemIds(second));
        controller.distinct = false;
        second.onDistinctChange(false);
        second.advance(1);
        assertEquals(Collections.singletonList(1), itemIds(second));
        second.removeFromMultiBlock(controller);
        second.controller = null;
        second.advance(1);
        assertTrue(itemIds(second).isEmpty());

        StockingTestHatch firstHatch = hatch();
        StockingTestHatch secondHatch = hatch();
        firstHatch.controller = controller;
        secondHatch.controller = controller;
        Collections.addAll(controller.fluids, firstHatch.slots());
        Collections.addAll(controller.fluids, secondHatch.slots());
        firstHatch.addToMultiBlock(controller);
        secondHatch.addToMultiBlock(controller);
        firstHatch.slots()[0].setConfig(fluid(0, 1));
        secondHatch.storage.contents.add(fluid(0, 1000000));
        FluidStack lava = new FluidStack(FluidRegistry.LAVA, 10);
        lava.tag = tag(1);
        secondHatch.storage.contents.add(AEFluidStack.fromFluidStack(lava));
        secondHatch.advance(1);
        assertEquals(Collections.singletonList(1), fluidIds(secondHatch));
    }

    @Test
    void missingWorldKeysStayOptInAndOldCardsPreservePolicyBeforeOneRefresh() {
        StockingTestBus bus = bus();
        StockingTestHatch hatch = hatch();
        bus.storage.contents.add(item(0, 10));
        hatch.storage.contents.add(fluid(0, 10));
        NBTTagCompound legacy = settings(10, 1);
        legacy.setBoolean("AutoPull", true);
        bus.readConfigFromTag(legacy);
        hatch.readConfigFromTag(legacy);
        assertTrue(bus.writeConfigToTag().getBoolean("PreferAbundant"));
        assertTrue(hatch.writeConfigToTag().getBoolean("PreferAbundant"));
        assertEquals(0, bus.storage.listScans);
        assertEquals(0, hatch.storage.listScans);
        bus.onConfigPasted();
        hatch.onConfigPasted();
        assertEquals(1, bus.storage.listScans);
        assertEquals(1, hatch.storage.listScans);
        assertEquals(1, bus.syncs);
        assertEquals(1, hatch.syncs);
        legacy.setBoolean("PreferAbundant", false);
        bus.readConfigFromTag(legacy);
        hatch.readConfigFromTag(legacy);
        assertFalse(bus.writeConfigToTag().getBoolean("PreferAbundant"));
        assertFalse(hatch.writeConfigToTag().getBoolean("PreferAbundant"));
        bus.readFromNBT(new NBTTagCompound());
        hatch.readFromNBT(new NBTTagCompound());
        assertFalse(bus.writeConfigToTag().getBoolean("PreferAbundant"));
        assertFalse(hatch.writeConfigToTag().getBoolean("PreferAbundant"));
        assertEquals(0, bus.storage.modulations);
        assertEquals(0, hatch.storage.modulations);
    }

    @Test
    void screenPolicyAndWronglyTypedNbtRespectBooleanSettings() throws Exception {
        MEStockingConfig config = new MEStockingConfig();
        AtomicInteger changes = new AtomicInteger();
        PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(false), true);
        config.addWidgets(new ModularPanel("stocking_policy"), manager, false, changes::incrementAndGet);
        MEStockingConfigTest.send(manager.findSyncHandler("me_stocking_abundant"), new BooleanSyncValue(() -> true));
        assertTrue(config.isPreferAbundant());
        assertEquals(1, changes.get());
        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setString("PreferAbundant", "true");
        config.readFromNBT(wrongType, true);
        assertTrue(config.isPreferAbundant());
        config.readFromNBT(wrongType, false);
        assertFalse(config.isPreferAbundant());
    }

    private static StockingTestBus bus() {
        StockingTestBus bus = new StockingTestBus();
        bus.addToMultiBlock(null);
        bus.readConfigFromTag(automaticSettings());
        return bus;
    }

    /** Carries one update from a server's config view to a client's, as the screen's packets would. */
    static <T extends IAEStack<T>, N> void sync(MEConfigSyncHandler<T, N> server, MEConfigSyncHandler<T, N> client,
                                                boolean init) {
        assertTrue(server.collectChanges(init));
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            server.writeChanges(packet);
            client.readOnClient(MEConfigSyncHandler.UPDATE_SLOTS, packet);
            assertEquals(0, packet.readableBytes(), "the reader must take the whole config and stock update");
        } finally {
            packet.release();
        }
    }

    private static void assertSameItem(ItemStack expected, ItemStack actual) {
        assertTrue(ItemStack.areItemsEqual(expected, actual), () -> expected + " shown as " + actual);
        assertTrue(ItemStack.areItemStackTagsEqual(expected, actual), () -> expected + " shown as " + actual);
    }

    private static StockingTestHatch hatch() {
        StockingTestHatch hatch = new StockingTestHatch();
        hatch.addToMultiBlock(null);
        hatch.readConfigFromTag(automaticSettings());
        return hatch;
    }

    private static NBTTagCompound automaticSettings() {
        NBTTagCompound settings = settings(10, 1);
        settings.setBoolean("AutoPull", true);
        settings.setBoolean("PreferAbundant", true);
        return settings;
    }

    private static NBTTagCompound tag(int id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Variant", id);
        return tag;
    }

    private static IAEItemStack item(int id, long amount) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.setTagCompound(tag(id));
        return AEItemStack.fromItemStack(stack).setStackSize(amount);
    }

    private static IAEFluidStack fluid(int id, long amount) {
        FluidStack stack = new FluidStack(FluidRegistry.WATER, 1);
        stack.tag = tag(id);
        return AEFluidStack.fromFluidStack(stack).setStackSize(amount);
    }

    private static int itemId(IAEItemStack stack) {
        return stack.copy().setStackSize(1).createItemStack().getTagCompound().getInteger("Variant");
    }

    private static int fluidId(IAEFluidStack stack) {
        return stack.copy().setStackSize(1).getFluidStack().tag.getInteger("Variant");
    }

    private static List<Integer> itemIds(StockingTestBus bus) {
        List<Integer> ids = new ArrayList<>();
        for (var slot : bus.slots()) {
            if (slot.getConfig() != null) ids.add(itemId(slot.getConfig()));
        }
        return ids;
    }

    private static List<Integer> fluidIds(StockingTestHatch hatch) {
        List<Integer> ids = new ArrayList<>();
        for (var slot : hatch.slots()) {
            if (slot.getConfig() != null) ids.add(fluidId(slot.getConfig()));
        }
        return ids;
    }
}

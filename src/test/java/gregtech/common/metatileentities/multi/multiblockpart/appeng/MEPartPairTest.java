package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;
import gregtech.api.capability.IControllable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.ConfigHolder;
import gregtech.common.inventory.appeng.SerializableFluidList;
import gregtech.common.inventory.appeng.SerializableItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAESlot;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

/** Each item part and its fluid twin share their code; these pin what both still do, and do alike. */
class MEPartPairTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void inputScreensOfferSixteenConfigSlotsAndTheirSideWidgets() {
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("layout_bus"));
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("layout_hatch"));
        for (MetaTileEntityMEInputPart<?, ?> part : Arrays.asList(bus, hatch, new StockingTestBus(),
                new StockingTestHatch())) {
            PanelSyncManager manager = manager();
            ModularPanel panel = part.buildUI(null, manager, new UISettings());
            assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
            assertEquals(16, widgets(panel, MEConfigSlotWidget.class).size());
            assertEquals(16, widgets(panel, MEStackDisplayWidget.class).size());
            assertNotNull(manager.findSyncHandlerNullable("me_config"));
            assertNotNull(manager.findSyncHandlerNullable("me_online"));
            boolean stocking = part instanceof StockingTestBus || part instanceof StockingTestHatch;
            // stocking parts have their settings below the slots, the others the amount of the selected slot
            assertEquals(stocking, manager.findSyncHandlerNullable("me_auto_pull") != null);
            assertEquals(stocking, manager.findSyncHandlerNullable("me_stocking_minimum") != null);
            assertEquals(stocking, manager.findSyncHandlerNullable("me_stocking_refresh") != null);
            assertEquals(stocking, manager.findSyncHandlerNullable("me_stocking_abundant") != null);
            boolean items = part instanceof MetaTileEntityMEInputBus;
            assertEquals(items ? 1 : 0, widgets(panel, GhostCircuitSlotWidget.class).size());
            long extraSlots = widgets(panel, ItemSlot.class).stream()
                    .filter(slot -> slot.isSynced()).count();
            assertEquals(items ? 1 : 0, extraSlots, "the bus's extra slot is its only own item slot");
        }
    }

    @Test
    void outputScreensShowTheirWaitingBuffer() {
        for (MetaTileEntityMEOutputPart<?, ?> part : Arrays.asList(
                new MetaTileEntityMEOutputBus(gregtechId("layout_output_bus")),
                new MetaTileEntityMEOutputHatch(gregtechId("layout_output_hatch")))) {
            PanelSyncManager manager = manager();
            ModularPanel panel = part.buildUI(null, manager, new UISettings());
            assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
            assertEquals(1, widgets(panel, MEBufferListWidget.class).size());
            assertNotNull(manager.findSyncHandlerNullable("me_buffer"));
            assertNotNull(manager.findSyncHandlerNullable("me_online"));
        }
    }

    @Test
    void outputBuffersReachTheScreenWithAmountsBeyondAnInt() {
        MetaTileEntityMEOutputBus bus = new MetaTileEntityMEOutputBus(gregtechId("buffer_bus"));
        MetaTileEntityMEOutputHatch hatch = new MetaTileEntityMEOutputHatch(gregtechId("buffer_hatch"));
        bus.getInternalBuffer().add(AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT))
                .setStackSize(5_000_000_000L));
        hatch.getInternalBuffer().add(AEFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, 1))
                .setStackSize(5_000_000_000L));
        MEBufferSyncHandler<IAEItemStack> itemServer = new MEBufferSyncHandler<>(MEStackType.ITEMS,
                bus.getInternalBuffer());
        MEBufferSyncHandler<IAEItemStack> itemClient = new MEBufferSyncHandler<>(MEStackType.ITEMS,
                new SerializableItemList());
        MEBufferSyncHandler<IAEFluidStack> fluidServer = new MEBufferSyncHandler<>(MEStackType.FLUIDS,
                hatch.getInternalBuffer());
        MEBufferSyncHandler<IAEFluidStack> fluidClient = new MEBufferSyncHandler<>(MEStackType.FLUIDS,
                new SerializableFluidList());
        sendBuffer(itemServer, itemClient);
        sendBuffer(fluidServer, fluidClient);
        assertEquals(Items.IRON_INGOT, itemClient.getEntries().get(0).createItemStack().getItem());
        assertEquals(5_000_000_000L, itemClient.getEntries().get(0).getStackSize());
        assertEquals(FluidRegistry.WATER, fluidClient.getEntries().get(0).getFluid());
        assertEquals(5_000_000_000L, fluidClient.getEntries().get(0).getStackSize());
        assertFalse(itemServer.collectChanges(false), "an unchanged buffer sends nothing");

        bus.getInternalBuffer().findPrecise(itemClient.getEntries().get(0)).setStackSize(0);
        assertTrue(itemServer.collectChanges(false));
        sendEntries(itemServer, itemClient);
        assertTrue(itemClient.getEntries().isEmpty(), "an entry the network took leaves the screen");
    }

    @Test
    void configEditsFromTheScreenAreCheckedByTheServer() throws Exception {
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("edit_bus"));
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("edit_hatch"));
        MEConfigSyncHandler<IAEItemStack, ItemStack> items = bus.createConfigSync();
        MEConfigSyncHandler<IAEFluidStack, FluidStack> fluids = hatch.createConfigSync();

        edit(items, MEConfigSyncHandler.SET_FROM_INGREDIENT, 4, buf -> MEStackType.ITEMS.writeNative(buf,
                new ItemStack(Items.IRON_INGOT, 3)));
        edit(fluids, MEConfigSyncHandler.SET_FROM_INGREDIENT, 4, buf -> MEStackType.FLUIDS.writeNative(buf,
                new FluidStack(FluidRegistry.WATER, 250)));
        assertEquals(3, bus.getAEItemHandler().getInventory()[4].getConfig().getStackSize());
        assertEquals(250, hatch.getAEFluidHandler().getInventory()[4].getConfig().getStackSize());

        for (MEConfigSyncHandler<?, ?> config : Arrays.asList(items, fluids)) {
            edit(config, MEConfigSyncHandler.SET_AMOUNT, 4, buf -> buf.writeVarLong(Long.MAX_VALUE));
            edit(config, MEConfigSyncHandler.SET_AMOUNT, 4, buf -> buf.writeVarLong(-8));
            edit(config, MEConfigSyncHandler.SET_AMOUNT, 99, buf -> buf.writeVarLong(5));
            edit(config, MEConfigSyncHandler.CLEAR, -1, buf -> {});
        }
        assertEquals(Integer.MAX_VALUE, bus.getAEItemHandler().getInventory()[4].getConfig().getStackSize());
        assertEquals(Integer.MAX_VALUE, hatch.getAEFluidHandler().getInventory()[4].getConfig().getStackSize());

        edit(items, MEConfigSyncHandler.CLEAR, 4, buf -> {});
        edit(fluids, MEConfigSyncHandler.CLEAR, 4, buf -> {});
        assertNull(bus.getAEItemHandler().getInventory()[4].getConfig());
        assertNull(hatch.getAEFluidHandler().getInventory()[4].getConfig());
    }

    @Test
    void stockingScreensTakeNoAmountsNorDuplicatesAndAutoPullLocksThem() throws Exception {
        StockingTestBus bus = new StockingTestBus();
        StockingTestHatch hatch = new StockingTestHatch();
        MEConfigSyncHandler<IAEItemStack, ItemStack> items = bus.createConfigSync();
        MEConfigSyncHandler<IAEFluidStack, FluidStack> fluids = hatch.createConfigSync();
        edit(items, MEConfigSyncHandler.SET_FROM_INGREDIENT, 0, buf -> MEStackType.ITEMS.writeNative(buf,
                new ItemStack(Items.IRON_INGOT)));
        edit(fluids, MEConfigSyncHandler.SET_FROM_INGREDIENT, 0, buf -> MEStackType.FLUIDS.writeNative(buf,
                new FluidStack(FluidRegistry.WATER, 1)));
        edit(items, MEConfigSyncHandler.SET_AMOUNT, 0, buf -> buf.writeVarLong(40));
        edit(fluids, MEConfigSyncHandler.SET_AMOUNT, 0, buf -> buf.writeVarLong(40));
        assertEquals(1, bus.slots()[0].getConfig().getStackSize());
        assertEquals(1, hatch.slots()[0].getConfig().getStackSize());

        // a stocking part shows each resource once
        edit(items, MEConfigSyncHandler.SET_FROM_INGREDIENT, 1, buf -> MEStackType.ITEMS.writeNative(buf,
                new ItemStack(Items.IRON_INGOT)));
        edit(fluids, MEConfigSyncHandler.SET_FROM_INGREDIENT, 1, buf -> MEStackType.FLUIDS.writeNative(buf,
                new FluidStack(FluidRegistry.WATER, 1)));
        assertNull(bus.slots()[1].getConfig());
        assertNull(hatch.slots()[1].getConfig());

        NBTTagCompound automatic = new NBTTagCompound();
        automatic.setBoolean("AutoPull", true);
        bus.readConfigFromTag(automatic);
        hatch.readConfigFromTag(automatic);
        bus.slots()[2].setConfig(item(1));
        hatch.slots()[2].setConfig(fluid(1));
        edit(items, MEConfigSyncHandler.CLEAR, 2, buf -> {});
        edit(fluids, MEConfigSyncHandler.CLEAR, 2, buf -> {});
        assertNotNull(bus.slots()[2].getConfig(), "auto-pull manages the configs");
        assertNotNull(hatch.slots()[2].getConfig(), "auto-pull manages the configs");
    }

    @Test
    void inputPartsSaveTheirSlotsUnderTheirOwnKeys() {
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("save_bus"));
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("save_hatch"));
        bus.getAEItemHandler().getInventory()[3].setConfig(item(32));
        bus.getAEItemHandler().getInventory()[3].setStock(item(20));
        hatch.getAEFluidHandler().getInventory()[3].setConfig(fluid(32));
        hatch.getAEFluidHandler().getInventory()[3].setStock(fluid(20));
        bus.setWorkingEnabled(false);
        hatch.setWorkingEnabled(false);

        NBTTagCompound busTag = bus.writeToNBT(new NBTTagCompound());
        NBTTagCompound hatchTag = hatch.writeToNBT(new NBTTagCompound());
        assertSlots(busTag.getTagList("ItemSlots", 10), "stack");
        assertSlots(hatchTag.getTagList("FluidTanks", 10), "tank");
        assertFalse(busTag.hasKey("FluidTanks"));
        assertFalse(hatchTag.hasKey("ItemSlots"));
        assertTrue(busTag.hasKey("ExtraInventory"));
        assertFalse(hatchTag.hasKey("ExtraInventory"));
        for (NBTTagCompound tag : Arrays.asList(busTag, hatchTag)) {
            assertTrue(tag.hasKey("WorkingEnabled", 1));
            assertFalse(tag.getBoolean("WorkingEnabled"));
            assertTrue(tag.hasKey("AllowExtraConnections", 1));
        }

        MetaTileEntityMEInputBus loadedBus = new MetaTileEntityMEInputBus(gregtechId("save_bus"));
        MetaTileEntityMEInputHatch loadedHatch = new MetaTileEntityMEInputHatch(gregtechId("save_hatch"));
        loadedBus.readFromNBT(busTag);
        loadedHatch.readFromNBT(hatchTag);
        assertFalse(loadedBus.isWorkingEnabled());
        assertFalse(loadedHatch.isWorkingEnabled());
        assertEquals(busTag.getTag("ItemSlots"), loadedBus.writeToNBT(new NBTTagCompound()).getTag("ItemSlots"));
        assertEquals(hatchTag.getTag("FluidTanks"),
                loadedHatch.writeToNBT(new NBTTagCompound()).getTag("FluidTanks"));
        assertEquals(20, loadedBus.getAEItemHandler().getInventory()[3].getStock().getStackSize());
        assertEquals(20, loadedHatch.getAEFluidHandler().getInventory()[3].getStock().getStackSize());

        loadedBus.readFromNBT(new NBTTagCompound());
        loadedHatch.readFromNBT(new NBTTagCompound());
        // saves without the flag keep the state the part already had
        assertFalse(loadedBus.isWorkingEnabled());
        assertFalse(loadedHatch.isWorkingEnabled());
    }

    @Test
    void dataSticksCopyConfigurationUnderEachPartsKey() {
        EntityPlayer player = player();
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("stick_bus"));
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("stick_hatch"));
        bus.getAEItemHandler().getInventory()[2].setConfig(item(5));
        hatch.getAEFluidHandler().getInventory()[2].setConfig(fluid(5));
        ItemStack busStick = new ItemStack(Items.PAPER);
        ItemStack hatchStick = new ItemStack(Items.PAPER);
        bus.onDataStickLeftClick(player, busStick);
        hatch.onDataStickLeftClick(player, hatchStick);

        NBTTagCompound busCopy = busStick.getTagCompound().getCompoundTag("MEInputBus");
        NBTTagCompound hatchCopy = hatchStick.getTagCompound().getCompoundTag("MEInputHatch");
        assertEquals(keys("ConfigStacks", "GhostCircuit"), busCopy.getKeySet());
        assertEquals(keys("ConfigStacks"), hatchCopy.getKeySet());
        assertEquals(keys("2"), busCopy.getCompoundTag("ConfigStacks").getKeySet());
        assertEquals(keys("2"), hatchCopy.getCompoundTag("ConfigStacks").getKeySet());
        // configs are saved as plain item and fluid stacks
        NBTTagCompound itemConfig = busCopy.getCompoundTag("ConfigStacks").getCompoundTag("2");
        NBTTagCompound fluidConfig = hatchCopy.getCompoundTag("ConfigStacks").getCompoundTag("2");
        assertEquals(new ItemStack(Items.IRON_INGOT, 5).serializeNBT(), itemConfig);
        assertEquals(new FluidStack(FluidRegistry.WATER, 5).writeToNBT(new NBTTagCompound()), fluidConfig);

        MetaTileEntityMEInputBus pastedBus = new MetaTileEntityMEInputBus(gregtechId("stick_bus"));
        MetaTileEntityMEInputHatch pastedHatch = new MetaTileEntityMEInputHatch(gregtechId("stick_hatch"));
        assertFalse(pastedBus.onDataStickRightClick(player, hatchStick));
        assertFalse(pastedHatch.onDataStickRightClick(player, busStick));
        assertTrue(pastedBus.onDataStickRightClick(player, busStick));
        assertTrue(pastedHatch.onDataStickRightClick(player, hatchStick));
        IAEItemStack pastedItem = pastedBus.getAEItemHandler().getInventory()[2].getConfig();
        IAEFluidStack pastedFluid = pastedHatch.getAEFluidHandler().getInventory()[2].getConfig();
        assertInstanceOf(WrappedItemStack.class, pastedItem);
        assertInstanceOf(WrappedFluidStack.class, pastedFluid);
        assertEquals(Items.IRON_INGOT, pastedItem.createItemStack().getItem());
        assertEquals(FluidRegistry.WATER, pastedFluid.getFluid());
        assertEquals(5, pastedItem.getStackSize());
        assertEquals(5, pastedFluid.getStackSize());
        assertNull(pastedBus.getAEItemHandler().getInventory()[0].getConfig());
        assertNull(pastedHatch.getAEFluidHandler().getInventory()[0].getConfig());
    }

    @Test
    void inputPartsFillTopUpAndReturnStockAlike() {
        StockingTestStorage<IAEItemStack> items = new StockingTestStorage<>(MEPartPairTest::sameItem);
        StockingTestStorage<IAEFluidStack> fluids = new StockingTestStorage<>(MEPartPairTest::sameFluid);
        items.acceptsInjections = true;
        fluids.acceptsInjections = true;
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("exchange_bus")) {

            @Override
            protected IMEMonitor<IAEItemStack> getMonitor() {
                return items.monitor;
            }

            @Override
            protected IActionSource getActionSource() {
                return items.actionSource;
            }
        };
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("exchange_hatch")) {

            @Override
            protected IMEMonitor<IAEFluidStack> getMonitor() {
                return fluids.monitor;
            }

            @Override
            protected IActionSource getActionSource() {
                return fluids.actionSource;
            }
        };
        ExportOnlyAESlot<IAEItemStack> itemSlot = bus.getAEItemHandler().getInventory()[0];
        ExportOnlyAESlot<IAEFluidStack> fluidSlot = hatch.getAEFluidHandler().getInventory()[0];
        items.contents.add(item(100));
        fluids.contents.add(fluid(100));

        itemSlot.setConfig(item(64));
        fluidSlot.setConfig(fluid(64));
        bus.syncME();
        hatch.syncME();
        assertEquals("64 held, 36 stored", amounts(itemSlot, items));
        assertEquals("64 held, 36 stored", amounts(fluidSlot, fluids));

        // a smaller config sends the excess back
        itemSlot.setConfig(item(10));
        fluidSlot.setConfig(fluid(10));
        bus.syncME();
        hatch.syncME();
        assertEquals("10 held, 90 stored", amounts(itemSlot, items));
        assertEquals("10 held, 90 stored", amounts(fluidSlot, fluids));

        // a cleared config sends everything back
        itemSlot.setConfig(null);
        fluidSlot.setConfig(null);
        bus.syncME();
        hatch.syncME();
        assertNull(itemSlot.getStock());
        assertNull(fluidSlot.getStock());
        assertEquals(100, items.contents.get(0).getStackSize());
        assertEquals(100, fluids.contents.get(0).getStackSize());

        itemSlot.setStock(item(7));
        fluidSlot.setStock(fluid(7));
        assertTrue(bus.flushInventory().isEmpty());
        assertTrue(hatch.flushInventory().isEmpty());
        assertNull(itemSlot.getStock());
        assertNull(fluidSlot.getStock());
        assertEquals(107, items.contents.get(0).getStackSize());
        assertEquals(107, fluids.contents.get(0).getStackSize());
        assertEquals(items.injections, fluids.injections);
        assertEquals(items.modulations, fluids.modulations);
        assertEquals(0, items.simulations);
        assertEquals(0, fluids.simulations);
    }

    @Test
    void outputPartsHandTheirBufferToTheNetworkAlike() {
        int previousInterval = ConfigHolder.compat.ae2.updateIntervals;
        try {
            ConfigHolder.compat.ae2.updateIntervals = 1;
            StockingTestStorage<IAEItemStack> items = new StockingTestStorage<>(MEPartPairTest::sameItem);
            StockingTestStorage<IAEFluidStack> fluids = new StockingTestStorage<>(MEPartPairTest::sameFluid);
            items.acceptsInjections = true;
            fluids.acceptsInjections = true;
            MetaTileEntityMEOutputBus bus = new MetaTileEntityMEOutputBus(gregtechId("output_test_bus")) {

                @Override
                public World getWorld() {
                    return DummyWorld.INSTANCE;
                }

                @Override
                public BlockPos getPos() {
                    return BlockPos.ORIGIN;
                }

                @Override
                public long getOffsetTimer() {
                    return 1;
                }

                @Override
                public boolean updateMEStatus() {
                    return true;
                }

                @Override
                protected IMEMonitor<IAEItemStack> getMonitor() {
                    return items.monitor;
                }

                @Override
                protected IActionSource getActionSource() {
                    return items.actionSource;
                }
            };
            MetaTileEntityMEOutputHatch hatch = new MetaTileEntityMEOutputHatch(gregtechId("output_test_hatch")) {

                @Override
                public World getWorld() {
                    return DummyWorld.INSTANCE;
                }

                @Override
                public BlockPos getPos() {
                    return BlockPos.ORIGIN;
                }

                @Override
                public long getOffsetTimer() {
                    return 1;
                }

                @Override
                public boolean updateMEStatus() {
                    return true;
                }

                @Override
                protected IMEMonitor<IAEFluidStack> getMonitor() {
                    return fluids.monitor;
                }

                @Override
                protected IActionSource getActionSource() {
                    return fluids.actionSource;
                }
            };
            SerializableItemList itemBuffer = new SerializableItemList();
            itemBuffer.add(AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 7)));
            SerializableFluidList fluidBuffer = new SerializableFluidList();
            fluidBuffer.add(AEFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, 7)));
            NBTTagCompound droppedBus = new NBTTagCompound();
            droppedBus.setTag(MetaTileEntityMEOutputBus.ITEM_BUFFER_TAG, itemBuffer.serializeNBT());
            NBTTagCompound droppedHatch = new NBTTagCompound();
            droppedHatch.setTag(MetaTileEntityMEOutputHatch.FLUID_BUFFER_TAG, fluidBuffer.serializeNBT());
            bus.initFromItemStackData(droppedBus);
            hatch.initFromItemStackData(droppedHatch);

            // a disabled part keeps its buffer
            bus.setWorkingEnabled(false);
            hatch.setWorkingEnabled(false);
            bus.update();
            hatch.update();
            assertEquals(0, items.injections);
            assertEquals(0, fluids.injections);
            NBTTagCompound busSave = bus.writeToNBT(new NBTTagCompound());
            NBTTagCompound hatchSave = hatch.writeToNBT(new NBTTagCompound());
            assertEquals(droppedBus.getTag("ItemBuffer"), busSave.getTag("ItemBuffer"));
            assertEquals(droppedHatch.getTag("FluidBuffer"), hatchSave.getTag("FluidBuffer"));
            assertFalse(busSave.getBoolean("WorkingEnabled"));
            assertFalse(hatchSave.getBoolean("WorkingEnabled"));

            bus.setWorkingEnabled(true);
            hatch.setWorkingEnabled(true);
            bus.update();
            hatch.update();
            assertEquals(1, items.injections);
            assertEquals(1, fluids.injections);
            assertEquals(7, items.contents.get(0).getStackSize());
            assertEquals(7, fluids.contents.get(0).getStackSize());
            NBTTagCompound busDrop = new NBTTagCompound();
            NBTTagCompound hatchDrop = new NBTTagCompound();
            bus.writeItemStackData(busDrop);
            hatch.writeItemStackData(hatchDrop);
            assertTrue(busDrop.isEmpty());
            assertTrue(hatchDrop.isEmpty());
        } finally {
            ConfigHolder.compat.ae2.updateIntervals = previousInterval;
        }
    }

    @Test
    void initialSyncPacketsCarryTheSameFieldsInTheSameOrder() {
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("packet_bus"));
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("packet_hatch"));
        MetaTileEntityMEOutputBus outputBus = new MetaTileEntityMEOutputBus(gregtechId("packet_output_bus"));
        MetaTileEntityMEOutputHatch outputHatch = new MetaTileEntityMEOutputHatch(gregtechId("packet_output_hatch"));
        StockingTestBus stockingBus = new StockingTestBus();
        StockingTestHatch stockingHatch = new StockingTestHatch();
        NBTTagCompound automatic = new NBTTagCompound();
        automatic.setBoolean("AutoPull", true);
        stockingBus.readConfigFromTag(automatic);
        stockingHatch.readConfigFromTag(automatic);
        for (MetaTileEntity part : Arrays.asList(bus, hatch, outputBus, outputHatch, stockingBus, stockingHatch)) {
            ((IControllable) part).setWorkingEnabled(false);
        }

        byte[] ordinary = initialSync(bus);
        assertArrayEquals(ordinary, initialSync(hatch));
        assertArrayEquals(ordinary, initialSync(outputBus));
        assertArrayEquals(ordinary, initialSync(outputHatch));
        // the working flag comes last, after the network state
        assertEquals(0, ordinary[ordinary.length - 1]);
        byte[] stocking = initialSync(stockingBus);
        assertArrayEquals(stocking, initialSync(stockingHatch));
        assertEquals(ordinary.length + 1, stocking.length);
        assertArrayEquals(ordinary, Arrays.copyOf(stocking, ordinary.length));
        // then the stocking parts' auto-pull flag
        assertEquals(1, stocking[stocking.length - 1]);

        StockingTestBus receivedBus = new StockingTestBus();
        StockingTestHatch receivedHatch = new StockingTestHatch();
        PacketBuffer busPacket = new PacketBuffer(Unpooled.wrappedBuffer(stocking));
        PacketBuffer hatchPacket = new PacketBuffer(Unpooled.wrappedBuffer(initialSync(stockingHatch)));
        receivedBus.receiveInitialSyncData(busPacket);
        receivedHatch.receiveInitialSyncData(hatchPacket);
        assertEquals(0, busPacket.readableBytes());
        assertEquals(0, hatchPacket.readableBytes());
        assertFalse(receivedBus.isWorkingEnabled());
        assertFalse(receivedHatch.isWorkingEnabled());
        assertTrue(receivedBus.writeConfigToTag().getBoolean("AutoPull"));
        assertTrue(receivedHatch.writeConfigToTag().getBoolean("AutoPull"));
    }

    private static byte[] initialSync(MetaTileEntity part) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        part.writeInitialSyncData(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    private static PanelSyncManager manager() {
        return new PanelSyncManager(new ModularSyncManager(false), true);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <W extends IWidget> List<W> widgets(ModularPanel panel, Class<W> type) {
        return (List<W>) (List<?>) TreeUtil.<IWidget, IWidget>flatListByType(panel, (Class) type);
    }

    private static void edit(MEConfigSyncHandler<?, ?> config, int action, int index,
                             Consumer<PacketBuffer> payload) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeVarInt(index);
            payload.accept(packet);
            config.readOnServer(action, packet);
        } finally {
            packet.release();
        }
    }

    private static <T extends IAEStack<T>> void sendBuffer(MEBufferSyncHandler<T> server,
                                                           MEBufferSyncHandler<T> client) {
        assertTrue(server.collectChanges(true));
        sendEntries(server, client);
    }

    private static <T extends IAEStack<T>> void sendEntries(MEBufferSyncHandler<T> server,
                                                            MEBufferSyncHandler<T> client) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            server.writeEntries(packet);
            client.readOnClient(MEBufferSyncHandler.UPDATE_ENTRIES, packet);
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    private static void assertSlots(NBTTagList slots, String entryKey) {
        assertEquals(16, slots.tagCount());
        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slot = slots.getCompoundTagAt(i);
            assertEquals(keys("slot", entryKey), slot.getKeySet());
            assertEquals(i, slot.getInteger("slot"));
            Set<String> expected = i == 3 ? keys("config", "stock") : Collections.emptySet();
            assertEquals(expected, slot.getCompoundTag(entryKey).getKeySet());
        }
    }

    private static <T extends IAEStack<T>> String amounts(ExportOnlyAESlot<T> slot, StockingTestStorage<T> storage) {
        return slot.getStock().getStackSize() + " held, " + storage.contents.get(0).getStackSize() + " stored";
    }

    private static Set<String> keys(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }

    private static boolean sameItem(IAEItemStack a, IAEItemStack b) {
        ItemStack left = a.copy().setStackSize(1).createItemStack();
        ItemStack right = b.copy().setStackSize(1).createItemStack();
        return left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static boolean sameFluid(IAEFluidStack a, IAEFluidStack b) {
        return a.copy().setStackSize(1).getFluidStack().isFluidEqual(b.copy().setStackSize(1).getFluidStack());
    }

    private static IAEItemStack item(int amount) {
        return WrappedItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, amount));
    }

    private static IAEFluidStack fluid(int amount) {
        return WrappedFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, amount));
    }

    private static EntityPlayer player() {
        return new EntityPlayer(DummyWorld.INSTANCE, new GameProfile(UUID.randomUUID(), "pair_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void sendStatusMessage(ITextComponent message, boolean actionBar) {}
        };
    }
}

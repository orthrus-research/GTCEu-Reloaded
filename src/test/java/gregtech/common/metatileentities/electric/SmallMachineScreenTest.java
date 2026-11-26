package gregtech.common.metatileentities.electric;

import gregtech.BootstrapExtension;
import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BootstrapExtension.class)
class SmallMachineScreenTest {

    @Test
    void chargerOffersEverySlotWithTheChargersInsertRules() {
        MetaTileEntityCharger charger = new MetaTileEntityCharger(gregtechId("screen_charger"), GTValues.LV, 4);
        PanelSyncManager manager = manager();
        ModularPanel panel = charger.buildUI(null, manager, new UISettings());
        assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));

        List<ModularSlot> slots = slotsOf(panel, charger.getImportItems());
        assertEquals(4, slots.size());
        assertNotNull(manager.getSlotGroup("charger_inv"));
        assertTrue(manager.getSlotGroup("charger_inv").allowShiftTransfer());
        for (ModularSlot slot : slots) {
            assertFalse(slot.isItemValid(new ItemStack(Blocks.DIRT)), "a charger slot takes only chargeable items");
        }
    }

    @Test
    void itemCollectorRangeStaysWithinItsTier() throws Exception {
        MetaTileEntityItemCollector collector = new MetaTileEntityItemCollector(gregtechId("screen_collector"),
                GTValues.LV, 8);
        PanelSyncManager manager = manager();
        ModularPanel panel = collector.buildUI(null, manager, new UISettings());
        assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
        IntSyncValue range = (IntSyncValue) manager.findSyncHandler("collect_range");

        send(range, 3);
        assertEquals(3, range(collector));
        send(range, Integer.MAX_VALUE);
        assertEquals(8, range(collector));
        send(range, Integer.MIN_VALUE);
        assertEquals(1, range(collector));

        // loading keeps the same bounds: no saved range is the tier's whole range, a saved one is clamped
        MetaTileEntityItemCollector loaded = new MetaTileEntityItemCollector(gregtechId("screen_collector"),
                GTValues.LV, 8);
        loaded.readFromNBT(new NBTTagCompound());
        assertEquals(8, range(loaded));
        NBTTagCompound tooFar = new NBTTagCompound();
        tooFar.setInteger("CollectRange", 1000);
        loaded.readFromNBT(tooFar);
        assertEquals(8, range(loaded));

        List<ModularSlot> slots = slotsOf(panel, collector.getExportItems());
        assertEquals(collector.getExportItems().getSlots(), slots.size());
        for (ModularSlot slot : slots) {
            assertFalse(slot.isItemValid(new ItemStack(Items.IRON_INGOT)), "collected items only come out");
        }
    }

    @Test
    void pumpTakesFluidContainersAndLocksAScreenFluidOnlyWhileEmpty() throws Exception {
        MetaTileEntityPump pump = new MetaTileEntityPump(gregtechId("screen_pump"), GTValues.LV);
        attach(pump);
        PanelSyncManager manager = manager();
        ModularPanel panel = pump.buildUI(null, manager, new UISettings());
        assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));

        ModularSlot input = slotsOf(panel, pump.getImportItems()).get(0);
        assertTrue(input.isItemValid(new ItemStack(Items.BUCKET)));
        assertFalse(input.isItemValid(new ItemStack(Blocks.DIRT)));
        ModularSlot output = slotsOf(panel, pump.getExportItems()).get(0);
        assertFalse(output.isItemValid(new ItemStack(Items.BUCKET)));

        GTFluidSyncHandler tank = (GTFluidSyncHandler) manager.findSyncHandler("pump_tank");
        pump.getExportFluids().fill(new FluidStack(FluidRegistry.WATER, 1000), true);
        lock(tank, new FluidStack(FluidRegistry.LAVA, 1000));
        assertFalse(pump.writeToNBT(new NBTTagCompound()).getBoolean("IsLocked"),
                "a tank holding fluid keeps its lock as it is");

        pump.getExportFluids().drain(Integer.MAX_VALUE, true);
        lock(tank, new FluidStack(FluidRegistry.LAVA, 1000));
        NBTTagCompound saved = pump.writeToNBT(new NBTTagCompound());
        assertTrue(saved.getBoolean("IsLocked"));
        FluidStack locked = FluidStack.loadFluidStackFromNBT(saved.getCompoundTag("LockedFluid"));
        assertEquals(FluidRegistry.LAVA, locked.getFluid());
        assertEquals(1, locked.amount);
    }

    static PanelSyncManager manager() {
        return new PanelSyncManager(new ModularSyncManager(false), true);
    }

    static void attach(MetaTileEntity machine) {
        new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
                setWorld(DummyWorld.INSTANCE);
            }
        };
    }

    static List<ModularSlot> slotsOf(ModularPanel panel, IItemHandler handler) {
        List<ModularSlot> slots = new ArrayList<>();
        for (ItemSlot slot : TreeUtil.<IWidget, ItemSlot>flatListByType(panel, ItemSlot.class)) {
            // the player's slots find their handlers only once the screen opens
            if (slot.isSynced() && slot.getSlot().getItemHandler() == handler) {
                slots.add(slot.getSlot());
            }
        }
        return slots;
    }

    static void send(IntSyncValue receiver, int value) throws Exception {
        IntSyncValue sender = new IntSyncValue(() -> value);
        sender.updateCacheFromSource(true);
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            receiver.readOnServer(0, buffer);
        } finally {
            buffer.release();
        }
    }

    private static void lock(GTFluidSyncHandler tank, FluidStack fluid) throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            NetworkUtils.writeFluidStack(buffer, fluid);
            tank.readOnServer(GTFluidSyncHandler.LOCK_FLUID, buffer);
        } finally {
            buffer.release();
        }
    }

    private static int range(MetaTileEntityItemCollector collector) {
        return collector.writeToNBT(new NBTTagCompound()).getInteger("CollectRange");
    }
}

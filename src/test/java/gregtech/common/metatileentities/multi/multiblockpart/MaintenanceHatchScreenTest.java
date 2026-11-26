package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.BootstrapExtension;
import gregtech.api.GTValues;
import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ExtendWith(BootstrapExtension.class)
class MaintenanceHatchScreenTest {

    @Test
    void configurableMultiplierButtonsStayWithinTheirBounds() throws Exception {
        MetaTileEntityMaintenanceHatch hatch = new MetaTileEntityMaintenanceHatch(
                gregtechId("screen_configurable_maintenance"), true);
        PanelSyncManager manager = manager();
        ModularPanel panel = hatch.buildUI(null, manager, new UISettings());
        assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));

        for (int i = 0; i < 30; i++) press(manager.findSyncHandler("maintenance_increase"));
        assertEquals(1.1, hatch.getDurationMultiplier(), 1e-9);
        for (int i = 0; i < 30; i++) press(manager.findSyncHandler("maintenance_decrease"));
        assertEquals(0.9, hatch.getDurationMultiplier(), 1e-9);
        assertEquals(hatch.getTimeMultiplier(), syncedDouble(manager, "maintenance_time"), 1e-9);
        assertEquals(0.9, syncedDouble(manager, "maintenance_duration"), 1e-9);
    }

    @Test
    void plainHatchOffersTheTapeSlotAndTheProblemMask() {
        // on April Fools' day the plain hatch shows the wiring task instead
        assumeFalse(GTValues.FOOLS.get());
        MetaTileEntityMaintenanceHatch hatch = new MetaTileEntityMaintenanceHatch(gregtechId("screen_maintenance"),
                false);
        PanelSyncManager manager = manager();
        ModularPanel panel = hatch.buildUI(null, manager, new UISettings());
        assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));

        assertNull(manager.findSyncHandlerNullable("maintenance_increase"), "a plain hatch has no multiplier");
        IntSyncValue problems = (IntSyncValue) manager.findSyncHandler("maintenance_problems");
        problems.updateCacheFromSource(true);
        assertEquals(hatch.getMaintenanceProblemsForDisplay(), problems.getIntValue());

        ModularSlot tape = null;
        for (ItemSlot slot : TreeUtil.<IWidget, ItemSlot>flatListByType(panel, ItemSlot.class)) {
            if (slot.isSynced() && slot.getSlot().getItemHandler() == hatch.getItemInventory()) {
                tape = slot.getSlot();
            }
        }
        assertNotNull(tape);
        assertFalse(tape.isItemValid(new ItemStack(Items.IRON_INGOT)), "the tape slot takes only tape");
        assertTrue(tape.isItemValid(MetaItems.DUCT_TAPE.getStackForm()));
    }

    private static PanelSyncManager manager() {
        return new PanelSyncManager(new ModularSyncManager(false), true);
    }

    private static double syncedDouble(PanelSyncManager manager, String key) {
        DoubleSyncValue value = (DoubleSyncValue) manager.findSyncHandler(key);
        value.updateCacheFromSource(true);
        return value.getDoubleValue();
    }

    private static void press(SyncHandler button) throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            new MouseData(Side.SERVER, 0, false, false, false).writeToPacket(buffer);
            button.readOnServer(1, buffer);
        } finally {
            buffer.release();
        }
    }
}

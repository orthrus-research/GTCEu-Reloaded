package gregtech.api.metatileentity;

import gregtech.BootstrapExtension;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.mui.widget.RecipeFailureWidget;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandler;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every electric single-block machine's screen shows each slot and tank of its inventories once, with the rules the
 * old screen had: inputs take and give items and receive shift-clicked items, outputs only give them, the charger slot
 * takes and gives but is no shift-click target, input tanks are filled and drained with containers and output tanks
 * only drained. It has the recipe progress bar, the failure indicator, an auto-output button for each kind of output
 * and, below ten outputs, the programmed circuit slot.
 */
@ExtendWith(BootstrapExtension.class)
class SimpleMachineScreenTest {

    @Test
    void everyMachineScreenShowsItsInventoriesWithTheirRules() {
        for (SimpleMachineMetaTileEntity machine : machines()) {
            String id = machine.metaTileEntityId.toString();
            ModularPanel panel = machine.buildUI(null,
                    new PanelSyncManager(new ModularSyncManager(false), true), new UISettings());
            List<IWidget> widgets = new ArrayList<>();
            collect(panel, widgets);

            checkItemSlots(machine, widgets, id);
            checkTanks(machine, widgets, id);

            List<RecipeProgressWidget> bars = ofType(widgets, RecipeProgressWidget.class);
            assertEquals(1, bars.size(), id);
            assertSame(machine.workable.getRecipeMap(), bars.get(0).getRecipeMap(), id);
            assertEquals(1, ofType(widgets, RecipeFailureWidget.class).size(), id);

            int outputKinds = (machine.exportItems.getSlots() > 0 ? 1 : 0) +
                    (machine.exportFluids.getTanks() > 0 ? 1 : 0);
            assertEquals(outputKinds, ofType(widgets, ToggleButton.class).size(), id);
            boolean circuit = machine.exportItems.getSlots() + machine.exportFluids.getTanks() <= 9;
            assertEquals(circuit ? 1 : 0, ofType(widgets, GhostCircuitSlotWidget.class).size(), id);
        }
    }

    private static void checkItemSlots(SimpleMachineMetaTileEntity machine, List<IWidget> widgets, String id) {
        Map<String, ModularSlot> slots = new HashMap<>();
        ModularSlot charger = null;
        for (ItemSlot widget : ofType(widgets, ItemSlot.class)) {
            // the player's inventory slots name their sync handler instead of holding one
            if (!widget.isSynced()) continue;
            ModularSlot slot = widget.getSlot();
            IItemHandler handler = slot.getItemHandler();
            if (handler == machine.chargerInventory) {
                assertNull(charger, id + " shows its charger twice");
                charger = slot;
                continue;
            }
            String side = handler == machine.importItems ? "in " : handler == machine.exportItems ? "out " : null;
            assertNotNull(side, id + " shows a slot of another inventory");
            assertNull(slots.put(side + slot.getSlotIndex(), slot), id + " shows a slot twice");
        }

        assertEquals(machine.importItems.getSlots() + machine.exportItems.getSlots(), slots.size(), id);
        for (int i = 0; i < machine.importItems.getSlots(); i++) {
            ModularSlot slot = slots.get("in " + i);
            assertTrue(slot.isCanPut() && slot.isCanTake(), id + " input " + i);
            assertNotNull(slot.getSlotGroup(), id + " input " + i + " takes no shift-clicked items");
            assertTrue(slot.getSlotGroup().allowShiftTransfer(), id + " input " + i);
        }
        for (int i = 0; i < machine.exportItems.getSlots(); i++) {
            ModularSlot slot = slots.get("out " + i);
            assertTrue(!slot.isCanPut() && slot.isCanTake(), id + " output " + i);
            assertNull(slot.getSlotGroup(), id + " output " + i);
        }
        assertNotNull(charger, id + " shows no charger slot");
        assertTrue(charger.isCanPut() && charger.isCanTake(), id);
        assertNull(charger.getSlotGroup(), id + " charger slot takes shift-clicked items");
    }

    private static void checkTanks(SimpleMachineMetaTileEntity machine, List<IWidget> widgets, String id) {
        Map<IFluidTank, String> tanks = new HashMap<>();
        for (int i = 0; i < machine.importFluids.getTanks(); i++) {
            tanks.put(machine.importFluids.getTankAt(i).getDelegate(), "in " + i);
        }
        for (int i = 0; i < machine.exportFluids.getTanks(); i++) {
            tanks.put(machine.exportFluids.getTankAt(i).getDelegate(), "out " + i);
        }

        List<String> shown = new ArrayList<>();
        for (GTFluidSlot widget : ofType(widgets, GTFluidSlot.class)) {
            GTFluidSyncHandler syncHandler = (GTFluidSyncHandler) widget.getSyncHandler();
            String tank = tanks.get(tankOf(syncHandler));
            assertNotNull(tank, id + " shows a tank of another inventory");
            assertTrue(syncHandler.canDrainSlot(), id + " " + tank);
            assertEquals(tank.startsWith("in"), syncHandler.canFillSlot(), id + " " + tank);
            shown.add(tank);
        }
        assertEquals(tanks.size(), shown.size(), id + " " + shown);
        assertEquals(tanks.size(), shown.stream().distinct().count(), id + " " + shown);
    }

    /** @return the tank a tank widget shows, which its sync handler keeps to itself */
    static IFluidTank tankOf(GTFluidSyncHandler syncHandler) {
        try {
            Field field = GTFluidSyncHandler.class.getDeclaredField("tank");
            field.setAccessible(true);
            return (IFluidTank) field.get(syncHandler);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static void collect(IWidget widget, List<IWidget> widgets) {
        widgets.add(widget);
        for (IWidget child : widget.getChildren()) collect(child, widgets);
    }

    static <T> List<T> ofType(List<IWidget> widgets, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (IWidget widget : widgets) {
            if (type.isInstance(widget)) found.add(type.cast(widget));
        }
        return found;
    }

    /** Every registered electric single-block machine, placed as the machine block places it. */
    private static List<SimpleMachineMetaTileEntity> machines() {
        List<SimpleMachineMetaTileEntity> machines = new ArrayList<>();
        for (MTERegistry registry : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity registered : registry) {
                if (registered instanceof SimpleMachineMetaTileEntity) {
                    MetaTileEntityHolder holder = new MetaTileEntityHolder();
                    machines.add((SimpleMachineMetaTileEntity) holder.setMetaTileEntity(registered));
                    holder.setWorld(DummyWorld.INSTANCE);
                }
            }
        }
        assertTrue(machines.size() > 100, "Found only " + machines.size() + " machines");
        return machines;
    }
}

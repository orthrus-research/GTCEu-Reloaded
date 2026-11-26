package gregtech.api.metatileentity;

import gregtech.BootstrapExtension;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.mui.widget.GTFluidSlot;

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

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.metatileentity.SimpleMachineScreenTest.collect;
import static gregtech.api.metatileentity.SimpleMachineScreenTest.ofType;
import static gregtech.api.metatileentity.SimpleMachineScreenTest.tankOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every single-block generator's screen shows its input slots and tanks, which take items and fluids as the old screen
 * did, and the recipe progress bar, with no outputs, charger or buttons.
 */
@ExtendWith(BootstrapExtension.class)
class SimpleGeneratorScreenTest {

    @Test
    void everyGeneratorScreenShowsItsInputs() {
        List<SimpleGeneratorMetaTileEntity> generators = generators();
        assertFalse(generators.isEmpty());
        for (SimpleGeneratorMetaTileEntity generator : generators) {
            String id = generator.metaTileEntityId.toString();
            assertFalse(generator.handlesRecipeOutputs, id + " shows its outputs; this test checks inputs only");
            ModularPanel panel = generator.buildUI(null,
                    new PanelSyncManager(new ModularSyncManager(false), true), new UISettings());
            List<IWidget> widgets = new ArrayList<>();
            collect(panel, widgets);

            List<Integer> slots = new ArrayList<>();
            for (ItemSlot widget : ofType(widgets, ItemSlot.class)) {
                if (!widget.isSynced()) continue;
                ModularSlot slot = widget.getSlot();
                assertSame(generator.importItems, slot.getItemHandler(), id);
                assertTrue(slot.isCanPut() && slot.isCanTake(), id);
                assertNotNull(slot.getSlotGroup(), id + " input takes no shift-clicked items");
                slots.add(slot.getSlotIndex());
            }
            assertEquals(generator.importItems.getSlots(), slots.size(), id);
            assertEquals(slots.size(), slots.stream().distinct().count(), id);

            List<GTFluidSlot> tanks = ofType(widgets, GTFluidSlot.class);
            assertEquals(generator.importFluids.getTanks(), tanks.size(), id);
            for (int i = 0; i < tanks.size(); i++) {
                GTFluidSyncHandler syncHandler = (GTFluidSyncHandler) tanks.get(i).getSyncHandler();
                assertSame(generator.importFluids.getTankAt(i).getDelegate(), tankOf(syncHandler), id);
                assertTrue(syncHandler.canDrainSlot() && syncHandler.canFillSlot(), id);
            }

            List<RecipeProgressWidget> bars = ofType(widgets, RecipeProgressWidget.class);
            assertEquals(1, bars.size(), id);
            assertSame(generator.workable.getRecipeMap(), bars.get(0).getRecipeMap(), id);
            assertTrue(ofType(widgets, ToggleButton.class).isEmpty(), id);
        }
    }

    /** Every registered single-block generator, placed as the machine block places it. */
    private static List<SimpleGeneratorMetaTileEntity> generators() {
        List<SimpleGeneratorMetaTileEntity> generators = new ArrayList<>();
        for (MTERegistry registry : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity registered : registry) {
                if (registered instanceof SimpleGeneratorMetaTileEntity) {
                    MetaTileEntityHolder holder = new MetaTileEntityHolder();
                    generators.add((SimpleGeneratorMetaTileEntity) holder.setMetaTileEntity(registered));
                    holder.setWorld(DummyWorld.INSTANCE);
                }
            }
        }
        return generators;
    }
}

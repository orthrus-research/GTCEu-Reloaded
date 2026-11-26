package gregtech.api.metatileentity;

import gregtech.BootstrapExtension;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeLayoutListing;
import gregtech.api.recipes.ui.RecipeLayoutSlot;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.world.DummyWorld;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each registered simple machine and generator lays out its screen's slots, tanks, progress bar and image at the
 * positions and with the textures its screen had on the old GUI system, with a slot or tank for each slot and tank of
 * its inventories.
 */
@ExtendWith(BootstrapExtension.class)
class RecipeMachineLayoutTest {

    @Test
    void everyMachineKeepsItsScreenLayout() throws Exception {
        Map<String, List<String>> kinds = new TreeMap<>();
        for (WorkableTieredMetaTileEntity machine : machines()) {
            List<String> lines = new ArrayList<>();
            for (String line : RecipeLayoutListing.describe(machine.createRecipeLayout(() -> 0))) {
                lines.add("  " + line);
            }
            List<String> previous = kinds.putIfAbsent(kind(machine), lines);
            assertTrue(previous == null || previous.equals(lines),
                    machine.metaTileEntityId + " lays out its screen unlike others of its kind");
        }

        List<String> found = new ArrayList<>();
        kinds.forEach((kind, lines) -> {
            found.add(kind);
            found.addAll(lines);
        });
        RecipeLayoutListing.check("machine_layouts.list", found);
    }

    @Test
    void everySlotShowsItsOwnInventorySlot() {
        for (WorkableTieredMetaTileEntity machine : machines()) {
            RecipeMapLayout layout = machine.createRecipeLayout(() -> 0);
            List<String> shown = new ArrayList<>();
            for (RecipeLayoutSlot slot : layout.getElements(RecipeLayoutSlot.class)) {
                shown.add((slot.isOutput() ? "out " : "in ") + (slot.isFluid() ? "fluid " : "item ") + slot.index());
            }
            List<String> expected = new ArrayList<>();
            addAll(expected, "in item ", machine.importItems.getSlots());
            addAll(expected, "in fluid ", machine.importFluids.getTanks());
            if (showsOutputs(machine)) {
                addAll(expected, "out item ", machine.exportItems.getSlots());
                addAll(expected, "out fluid ", machine.exportFluids.getTanks());
            }
            shown.sort(null);
            expected.sort(null);
            assertEquals(expected, shown, machine.metaTileEntityId.toString());
            assertEquals(RecipeMapUI.HEIGHT + machine.getRecipeLayoutOffset(), layout.getHeight(),
                    machine.metaTileEntityId.toString());
        }
    }

    private static void addAll(List<String> list, String prefix, int count) {
        for (int i = 0; i < count; i++) list.add(prefix + i);
    }

    private static String kind(WorkableTieredMetaTileEntity machine) {
        RecipeMap<?> map = machine.workable.getRecipeMap();
        boolean outputs = showsOutputs(machine);
        return "machine " + map.getUnlocalizedName() + " items " + machine.importItems.getSlots() + '/' +
                (outputs ? machine.exportItems.getSlots() : 0) + " fluids " + machine.importFluids.getTanks() + '/' +
                (outputs ? machine.exportFluids.getTanks() : 0) + " offset " + machine.getRecipeLayoutOffset();
    }

    private static boolean showsOutputs(WorkableTieredMetaTileEntity machine) {
        return !(machine instanceof SimpleGeneratorMetaTileEntity) || machine.handlesRecipeOutputs;
    }

    /** Every registered simple machine and generator, placed as the machine block places it. */
    private static List<WorkableTieredMetaTileEntity> machines() {
        List<WorkableTieredMetaTileEntity> machines = new ArrayList<>();
        for (MTERegistry registry : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity registered : registry) {
                if (registered instanceof SimpleMachineMetaTileEntity ||
                        registered instanceof SimpleGeneratorMetaTileEntity) {
                    MetaTileEntityHolder holder = new MetaTileEntityHolder();
                    WorkableTieredMetaTileEntity machine = (WorkableTieredMetaTileEntity) holder
                            .setMetaTileEntity(registered);
                    holder.setWorld(DummyWorld.INSTANCE);
                    machines.add(machine);
                }
            }
        }
        assertTrue(machines.size() > 100, "Found only " + machines.size() + " machines");
        return machines;
    }
}

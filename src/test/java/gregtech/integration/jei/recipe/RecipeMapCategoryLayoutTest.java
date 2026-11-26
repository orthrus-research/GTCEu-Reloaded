package gregtech.integration.jei.recipe;

import gregtech.BootstrapExtension;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.ui.RecipeLayoutListing;
import gregtech.api.recipes.ui.RecipeLayoutSlot;
import gregtech.api.recipes.ui.RecipeMapLayout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each recipe map's recipe viewer category shows the slots, tanks, progress bars and images, at the positions and with
 * the textures, the category drew on the old GUI system, and gives each recipe ingredient one slot or tank of its own.
 */
@ExtendWith(BootstrapExtension.class)
class RecipeMapCategoryLayoutTest {

    @Test
    void everyMapKeepsItsRecipeViewerLayout() throws Exception {
        List<String> found = new ArrayList<>();
        for (RecipeMap<?> map : recipeMaps()) {
            RecipeMapLayout layout = RecipeMapCategory.createLayout(map);
            found.add("map " + map.getUnlocalizedName() + ' ' + layout.getWidth() + 'x' + layout.getHeight() +
                    (map.getRecipeMapUI().isJEIVisible() ? "" : " hidden"));
            for (String line : RecipeLayoutListing.describe(layout)) found.add("  " + line);
        }
        RecipeLayoutListing.check("jei_layouts.list", found);
    }

    @Test
    void everyIngredientHasOneSlot() throws Exception {
        for (RecipeMap<?> map : recipeMaps()) {
            RecipeMapLayout layout = RecipeMapCategory.createLayout(map);
            Set<String> seen = new HashSet<>();
            for (RecipeLayoutSlot slot : layout.getElements(RecipeLayoutSlot.class)) {
                int count = slot.isFluid() ? (slot.isOutput() ? map.getMaxFluidOutputs() : map.getMaxFluidInputs()) :
                        (slot.isOutput() ? map.getMaxOutputs() : RecipeMapCategory.importSlotCount(map));
                assertTrue(slot.index() >= 0 && slot.index() < count, map.getUnlocalizedName() + ": " + slot);
                assertTrue(seen.add(slot.isOutput() + " " + slot.isFluid() + " " + slot.index()),
                        map.getUnlocalizedName() + " shows twice: " + slot);
            }
        }
    }

    /** The maps GTCEu registers, by name, without maps other tests may have registered. */
    private static List<RecipeMap<?>> recipeMaps() throws IllegalAccessException {
        List<RecipeMap<?>> maps = new ArrayList<>();
        for (Field field : RecipeMaps.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && RecipeMap.class.isAssignableFrom(field.getType())) {
                maps.add((RecipeMap<?>) field.get(null));
            }
        }
        maps.sort(Comparator.comparing(RecipeMap::getUnlocalizedName));
        return maps;
    }
}

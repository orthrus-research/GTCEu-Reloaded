package gregtech.api.capability.impl;

import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.capability.impl.PaintGroupSelectionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeInputProviderTest {

    private static DualHatchTestItems items;

    @BeforeAll
    static void setup() throws Exception {
        items = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws Exception {
        items.close();
    }

    @Test
    void privateViewsWorkWithoutPaintModeAndNeverMergeSameColorBatchesOrOrdinaryInputs() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT), new ItemStack(Items.IRON_INGOT)));
        PaintGroupTestProvider first = new PaintGroupTestProvider(RED), second = new PaintGroupTestProvider(RED);
        first.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        second.privateItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        PaintGroupTestPart ordinary = input(-1, Items.IRON_INGOT, 1), target = output(BLUE);
        PaintGroupTestController controller = new PaintGroupTestController(map, first, second, ordinary, target);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        first.privateItems.setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        first.changed();
        assertTrue(logic.shouldSearchForRecipes());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(-3, logic.getActiveOutputColor());
        assertTrue(first.privateItems.getStackInSlot(0).isEmpty());
        assertEquals(1, second.privateItems.getStackInSlot(0).getCount());
        assertEquals(1, ordinary.getImportItems().getStackInSlot(0).getCount());
        assertEquals(1, first.starts);
        assertEquals(0, second.starts);
        logic.tryCompleteRecipe();
        assertEquals(1, target.getExportItems().getStackInSlot(0).getCount());
    }

    @Test
    void attachedConsumingPartSuppliesRouteAndSharedBatchKeysAreDeduplicatedAcrossProxies() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        ItemStackHandler ledger = new ItemStackHandler(1);
        ledger.setStackInSlot(0, new ItemStack(Items.WHEAT));
        PaintGroupTestProvider red = new PaintGroupTestProvider(RED, ledger),
                blue = new PaintGroupTestProvider(BLUE, ledger);
        blue.key = red.key;
        PaintGroupTestPart redOutput = output(RED), blueOutput = output(BLUE);
        PaintGroupTestController controller = grouped(map, red, blue, redOutput, blueOutput);
        assertEquals(1, controller.getRecipeInputViews().getRecipeInputViews().stream()
                .filter(v -> v.hasPrivateInputs()).count());
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(RED, controller.getRecipeMapWorkable().getActiveOutputColor());
        assertEquals(1, red.starts);
        assertEquals(0, blue.starts);
        controller.getRecipeMapWorkable().tryCompleteRecipe();
        assertEquals(1, redOutput.getExportItems().getStackInSlot(0).getCount());
        assertTrue(blueOutput.getExportItems().getStackInSlot(0).isEmpty());
    }

    @Test
    void staleDetachedViewCannotConsumeAfterTheSelectedLegacyStartCheck() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        PaintGroupTestController controller = grouped(map, provider, output(RED));
        controller.beforeStart = () -> provider.owner = null;
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(0, controller.getRecipeMapWorkable().getProgress());
        assertEquals(1, provider.privateItems.getStackInSlot(0).getCount());
        assertEquals(0, provider.starts);
        assertNotEquals(RecipeFailureReason.NONE, controller.getRecipeMapWorkable().getRecipeFailure().getReason());
    }

    @Test
    void successfulObserverRunsAfterFullConsumptionAndMayPruneWithoutLinkingActiveOutputs() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).fluidInputs(water(100)));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        provider.privateFluids.fill(water(100), true);
        provider.pruneOnStart = true;
        PaintGroupTestPart target = output(RED);
        PaintGroupTestController controller = grouped(map, provider, target);
        controller.getRecipeMapWorkable().trySearchNewRecipe();
        assertEquals(1, provider.starts);
        assertFalse(provider.available);
        assertTrue(provider.privateItems.getStackInSlot(0).isEmpty());
        assertEquals(0, provider.privateFluids.getTankAt(0).getFluidAmount());
        controller.getRecipeMapWorkable().tryCompleteRecipe();
        assertFalse(controller.getRecipeMapWorkable().isAwaitingOutputs());
        assertEquals(1, target.getExportItems().getStackInSlot(0).getCount());
        assertEquals(1, provider.starts);
    }

    @Test
    void onlyTheSelectedProvidersRevisionMustRemainStableAcrossLegacyStartCallbacks() {
        for (boolean selectedChanged : new boolean[] { false, true }) {
            RecipeMap<SimpleRecipeBuilder> map = map();
            compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
            PaintGroupTestProvider first = new PaintGroupTestProvider(RED), second = new PaintGroupTestProvider(BLUE);
            first.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
            PaintGroupTestController controller = new PaintGroupTestController(map, first, second, output(-1));
            controller.beforeStart = () -> {
                if (selectedChanged) first.revision++;
                else second.revision++;
            };
            MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
            logic.trySearchNewRecipe();
            assertEquals(selectedChanged ? 0 : 1, logic.getProgress());
            assertEquals(selectedChanged ? 1 : 0, first.privateItems.getStackInSlot(0).getCount());
            assertEquals(selectedChanged ? 0 : 1, first.starts);
            assertEquals(0, second.starts);
            if (selectedChanged) {
                assertEquals(RecipeFailureReason.INPUT_CHANGED, logic.getRecipeFailure().getReason());
                logic.trySearchNewRecipe();
                assertEquals(1, logic.getProgress());
                assertEquals(1, first.starts);
            }
        }
    }

    @Test
    void repaintWithPaintModeOffRebuildsPrivateViewsAndKeepsLegacyOutputRouting() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        PaintGroupTestPart target = output(RED);
        PaintGroupTestController controller = new PaintGroupTestController(map, provider, target);
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        assertTrue(controller.getRecipeInputViews().isAvailable()); // Capture old decorative colors first.
        provider.setPaintingColor(BLUE);
        target.setPaintingColor(0xFFFFFF);
        assertFalse(controller.isPaintGroupingEnabled());
        assertTrue(controller.getRecipeInputViews().isAvailable());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(-3, logic.getActiveOutputColor());
        target.setPaintingColor(RED);
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(1, target.getExportItems().getStackInSlot(0).getCount());
        assertEquals(1, provider.starts);
    }

    @Test
    void providerAvailabilityRevisionWakesAnUnnotifiedFailedSearch() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        PaintGroupTestController controller = new PaintGroupTestController(map, provider, output(-1));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        assertTrue(logic.shouldSearchForRecipes());
        logic.trySearchNewRecipe();
        controller.getNotifiedItemInputList().clear();
        controller.getNotifiedFluidInputList().clear();
        for (controller.tick = 1; controller.tick <= 40; controller.tick++)
            assertFalse(logic.shouldSearchForRecipes(),
                    "Unchanged providers must not repeatedly invalidate failed input searches");
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        provider.revision++;
        assertTrue(logic.shouldSearchForRecipes());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
    }
}

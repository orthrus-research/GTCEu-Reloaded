package gregtech.api.capability.impl;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.recipes.modifiers.RecipeModification;
import gregtech.api.recipes.modifiers.RecipeModifier;
import gregtech.api.recipes.modifiers.RecipeModifierList;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.api.capability.impl.PaintGroupSelectionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierViewTest {

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
    void eachPrivateViewGetsOneAttemptAndUsesItsOwnPairedItemsAndFluids() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).fluidInputs(water(25)));
        PaintGroupTestProvider first = new PaintGroupTestProvider(RED), second = new PaintGroupTestProvider(BLUE);
        first.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        second.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT, 3));
        first.privateFluids.fill(water(50), true);
        second.privateFluids.fill(water(100), true);
        ModifierViewController controller = new ModifierViewController(map, first, second, output(BLUE));
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierList.of(RecipeModifier.named("test:paired", (context, view) -> {
            calls.incrementAndGet();
            return RecipeModification.builder().itemInputs(2, 0).fluidInputs(4, 0).build();
        }));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(2, calls.get());
        assertEquals(3, controller.providerReads, "One empty ordinary view precedes the two private views");
        assertEquals(2, first.privateItems.getStackInSlot(0).getCount());
        assertEquals(50, first.privateFluids.getTankAt(0).getFluidAmount());
        assertEquals(1, second.privateItems.getStackInSlot(0).getCount());
        assertEquals(0, second.privateFluids.getTankAt(0).getFluidAmount());
        assertEquals(0, first.starts);
        assertEquals(1, second.starts);
    }

    @Test
    void selectedProviderRevisionChangedDuringModifierRejectsBeforeConsumptionAndNextAttemptIsFresh() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        ModifierViewController controller = new ModifierViewController(map, provider, output(RED));
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierList.of(RecipeModifier.named("test:revision", (context, view) -> {
            if (calls.incrementAndGet() == 1) provider.changed(); // Adversarial callback violates purity.
            return RecipeModification.builder().itemInputs(2, 0).build();
        }));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertEquals(0, logic.getProgress());
        assertEquals(2, provider.privateItems.getStackInSlot(0).getCount());
        assertEquals(0, provider.starts);
        assertEquals(RecipeFailureReason.INPUT_CHANGED, logic.getRecipeFailure().getReason());
        assertFalse(logic.hasRecipeModifierAttempt());
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(2, calls.get());
        assertTrue(provider.privateItems.getStackInSlot(0).isEmpty());
        assertEquals(1, provider.starts);
    }

    @Test
    void sameViewFallbackIsCachedThroughSelectedCheckAndSetup() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe first = map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).outputs(new ItemStack(Items.APPLE))
                .duration(8).EUt(1).build().getResult();
        Recipe second = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.BREAD))
                .duration(8).EUt(1).build().getResult();
        assertTrue(map.compileRecipe(first));
        assertTrue(map.compileRecipe(second));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        provider.privateItems.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 2));
        ModifierViewController controller = new ModifierViewController(map, provider, output(RED));
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierList.of(RecipeModifier.named("test:fallback", (context, view) -> {
            calls.incrementAndGet();
            return view.itemOutputs().get(0).getItem() == Items.APPLE ? RecipeModification.cancel("test.blocked") :
                    RecipeModification.builder().itemInputs(2, 0).build();
        }));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertSame(second, logic.previousRecipe);
        assertEquals(2, calls.get());
        assertEquals(2, controller.providerReads, "One empty ordinary view and one complete private-view attempt");
        assertEquals(1, provider.privateItems.getStackInSlot(0).getCount());
        assertTrue(provider.privateItems.getStackInSlot(1).isEmpty());
        assertEquals(1, provider.starts);
        assertFalse(logic.hasRecipeModifierAttempt());
    }

    @Test
    void fatalCallbackStillClearsViewAndAttemptAndLaterHealthyAttemptWorks() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestProvider provider = new PaintGroupTestProvider(RED);
        provider.privateItems.setStackInSlot(0, new ItemStack(Items.WHEAT));
        ModifierViewController controller = new ModifierViewController(map, provider, output(RED));
        controller.modifiers = RecipeModifierList.of(RecipeModifier.named("test:fatal", (context, view) -> {
            throw new OutOfMemoryError("sentinel");
        }));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        assertThrows(OutOfMemoryError.class, logic::trySearchNewRecipe);
        assertFalse(logic.hasRecipeModifierAttempt());
        assertSame(controller.getInputInventory(), logic.getInputInventory());
        assertEquals(1, provider.privateItems.getStackInSlot(0).getCount());
        controller.modifiers = RecipeModifierList
                .of(RecipeModifier.named("test:healthy", (context, view) -> RecipeModification.IDENTITY));
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(1, provider.starts);
    }

    @Test
    void cachedDistinctCancellationAndSameBusFallbackShareOneProviderSnapshot() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)).outputs(new ItemStack(Items.APPLE))
                .duration(8).EUt(1).build().getResult();
        Recipe allowed = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.BREAD))
                .duration(8).EUt(1).build().getResult();
        assertTrue(map.compileRecipe(blocked));
        assertTrue(map.compileRecipe(allowed));
        PaintGroupTestPart bus = input(RED, Items.WHEAT, 1);
        bus.getImportItems().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 2));
        ModifierViewController controller = new ModifierViewController(map, bus, output(-1));
        controller.setDistinct(true);
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierList.of(RecipeModifier.named("test:distinct_cached", (context, view) -> {
            calls.incrementAndGet();
            return view.itemOutputs().get(0).getItem() == Items.APPLE ? RecipeModification.cancel("test.blocked") :
                    RecipeModification.builder().itemInputs(2, 0).build();
        }));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.previousRecipe = blocked;
        logic.trySearchNewRecipe();
        assertSame(allowed, logic.previousRecipe);
        assertEquals(1, logic.getProgress());
        assertEquals(2, calls.get());
        assertEquals(1, controller.providerReads);
        assertEquals(1, bus.getImportItems().getStackInSlot(0).getCount());
        assertTrue(bus.getImportItems().getStackInSlot(1).isEmpty());
    }

    @Test
    void legacyDistinctBusesUseAdjustedQuantitiesAndDoNotShareFailedAttempts() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        PaintGroupTestPart first = input(RED, Items.WHEAT, 1), second = input(BLUE, Items.WHEAT, 3);
        ModifierViewController controller = new ModifierViewController(map, first, second, output(-1));
        controller.setDistinct(true);
        AtomicInteger calls = new AtomicInteger();
        controller.modifiers = RecipeModifierList.of(RecipeModifier.named("test:distinct", (context, view) -> {
            calls.incrementAndGet();
            return RecipeModification.builder().itemInputs(2, 0).build();
        }));
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        logic.trySearchNewRecipe();
        assertEquals(1, logic.getProgress());
        assertEquals(2, calls.get());
        assertEquals(1, first.getImportItems().getStackInSlot(0).getCount());
        assertEquals(1, second.getImportItems().getStackInSlot(0).getCount());
        assertFalse(logic.hasRecipeModifierAttempt());
    }
}

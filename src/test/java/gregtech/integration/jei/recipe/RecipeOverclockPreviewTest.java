package gregtech.integration.jei.recipe;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.properties.impl.ComputationProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.recipes.properties.impl.PrimitiveProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.recipes.properties.impl.TotalComputationProperty;
import gregtech.api.util.LocalizationUtils;
import gregtech.integration.jei.utils.JeiButton;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeOverclockPreviewTest {

    private static RecipeMap<SimpleRecipeBuilder> recipeMap;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        recipeMap = new RecipeMapBuilder<>("q1_overclock_preview", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    @Test
    void normalAndPerfectEstimatesStartFromTheBaseRecipe() {
        RecipeOverclockPreview preview = preview(30, 200);
        assertTrue(preview.click(0, false));
        assertEquals(GTValues.MV, preview.tier());
        assertResult(preview, 120, 100);
        assertEquals(12000, preview.eut() * preview.duration());

        assertTrue(preview.click(0, true));
        assertEquals(GTValues.HV, preview.tier());
        assertResult(preview, 480, 12);
        assertTrue(preview.isPerfect());

        assertTrue(preview.click(1, false));
        assertResult(preview, 120, 100);
        assertFalse(preview.isPerfect());
    }

    @Test
    void ulvReceivesItsFirstOverclockAtMv() {
        RecipeOverclockPreview preview = preview(8, 200);
        assertTrue(preview.click(0, false));
        assertEquals(GTValues.LV, preview.tier());
        assertResult(preview, 8, 200);
        assertTrue(preview.click(0, false));
        assertEquals(GTValues.MV, preview.tier());
        assertResult(preview, 32, 100);
    }

    @Test
    void voltageBoundariesUseTheLowestSufficientTier() {
        RecipeOverclockPreview preview = preview(33, 200);
        assertEquals(GTValues.MV, preview.tier());
        assertTrue(preview.click(0, false));
        assertEquals(GTValues.HV, preview.tier());
        assertResult(preview, 132, 100);
    }

    @Test
    void oneTickFloorNeverPaysForAnOverclockThatCannotReduceDuration() {
        RecipeOverclockPreview preview = preview(30, 3);
        assertTrue(preview.click(0, false));
        assertResult(preview, 120, 1);
        assertTrue(preview.click(0, false));
        assertResult(preview, 120, 1);

        preview.reset();
        assertTrue(preview.click(0, true));
        assertResult(preview, 30, 3);

        RecipeOverclockPreview alreadyOneTick = preview(30, 1);
        assertTrue(alreadyOneTick.click(0, false));
        assertResult(alreadyOneTick, 30, 1);
    }

    @Test
    void clicksAreBoundedAndResetRestoresBothTierAndMode() {
        RecipeOverclockPreview preview = preview(30, 201);
        assertTrue(preview.click(1, true));
        assertEquals(GTValues.LV, preview.tier());
        assertResult(preview, 30, 201);
        for (int i = 0; i < GTValues.MAX + 5; i++) assertTrue(preview.click(0, true));
        assertEquals(GTValues.MAX, preview.tier());
        long eut = preview.eut();
        int duration = preview.duration();
        assertFalse(preview.click(3, false));
        assertResult(preview, eut, duration);
        assertTrue(preview.isPerfect());

        assertTrue(preview.click(2, true));
        assertEquals(GTValues.LV, preview.tier());
        assertResult(preview, 30, 201);
        assertFalse(preview.isPerfect());
    }

    @Test
    void maximumTierUsesLongEnergyAndDoesNotWrap() {
        RecipeOverclockPreview preview = preview(GTValues.V[GTValues.OpV], Integer.MAX_VALUE);
        assertTrue(preview.click(0, false));
        assertEquals(GTValues.MAX, preview.tier());
        assertResult(preview, 2147483648L, 1073741823);
        assertEquals(2305843007066210304L, preview.eut() * preview.duration());
        assertTrue(preview.click(0, false));
        assertResult(preview, 2147483648L, 1073741823);
    }

    @Test
    void fusionAndComputationRemainUnchangedWithAnExplanation() {
        RecipePropertyStorage fusion = new RecipePropertyStorageImpl();
        fusion.store(FusionEUToStartProperty.getInstance(), 160000000L);
        assertUnavailable(recipe(32768, 200, fusion), false, "gregtech.jei.overclock.fusion");

        RecipePropertyStorage computation = new RecipePropertyStorageImpl();
        computation.store(ComputationProperty.getInstance(), 32);
        assertUnavailable(recipe(30, 200, computation), false, "gregtech.jei.overclock.computation");
        RecipePropertyStorage totalComputation = new RecipePropertyStorageImpl();
        totalComputation.store(TotalComputationProperty.getInstance(), 6400);
        assertUnavailable(recipe(30, 200, totalComputation), false, "gregtech.jei.overclock.computation");

        TestRecipeWrapper wrapper = new TestRecipeWrapper(recipe(32768, 200, fusion));
        wrapper.prepareOverclockPreview(176, 126);
        List<String> tooltip = new ArrayList<>();
        wrapper.previewButton().buildTooltip(tooltip);
        assertTrue(tooltip.contains(LocalizationUtils.format("gregtech.jei.overclock.unavailable")));
        assertTrue(tooltip.contains(LocalizationUtils.format("gregtech.jei.overclock.fusion")));
    }

    @Test
    void generatorsPrimitiveAndInvalidRecipesCannotBeOverclocked() {
        assertUnavailable(recipe(30, 200, RecipePropertyStorage.EMPTY), true, "gregtech.jei.overclock.generator");
        assertUnavailable(recipe(-30, 200, RecipePropertyStorage.EMPTY), false, "gregtech.jei.overclock.generator");
        RecipePropertyStorage primitive = new RecipePropertyStorageImpl();
        primitive.store(PrimitiveProperty.getInstance(), true);
        assertUnavailable(recipe(30, 200, primitive), false, "gregtech.jei.overclock.custom");
        assertUnavailable(recipe(0, 200, RecipePropertyStorage.EMPTY), false, "gregtech.jei.overclock.invalid");
        assertUnavailable(recipe(30, 0, RecipePropertyStorage.EMPTY), false, "gregtech.jei.overclock.invalid");
        assertUnavailable(recipe(GTValues.V[GTValues.MAX] + 1, 200, RecipePropertyStorage.EMPTY), false,
                "gregtech.jei.overclock.invalid");
    }

    @Test
    void wrappersHaveIndependentStateAndNeverChangeTheirSharedRecipe() {
        RecipePropertyStorage properties = new RecipePropertyStorageImpl();
        properties.store(TemperatureProperty.getInstance(), 1500);
        Recipe recipe = recipe(30, 201, properties);
        var inputs = recipe.getInputs();
        var outputs = recipe.getOutputs();
        ItemStack output = outputs.get(0).copy();
        int originalHash = recipe.hashCode();
        TestRecipeWrapper first = new TestRecipeWrapper(recipe);
        TestRecipeWrapper second = new TestRecipeWrapper(recipe);
        first.overclockPreview.click(0, true);
        first.overclockPreview.click(0, true);
        assertResult(first.overclockPreview, 480, 12);
        assertResult(second.overclockPreview, 30, 201);
        assertSame(recipe, first.getRecipe());
        assertSame(recipe, second.getRecipe());
        assertEquals(30, recipe.getEUt());
        assertEquals(201, recipe.getDuration());
        assertSame(inputs, recipe.getInputs());
        assertEquals(2, inputs.get(0).getAmount());
        assertSame(outputs, recipe.getOutputs());
        assertTrue(ItemStack.areItemStacksEqual(output, outputs.get(0)));
        assertSame(properties, recipe.propertyStorage());
        assertEquals(1500, recipe.getProperty(TemperatureProperty.getInstance(), 0).intValue());
        assertEquals(originalHash, recipe.hashCode());
    }

    @Test
    void assigningALayoutResetsThePreviewAndDoesNotAccumulateButtons() {
        TestRecipeWrapper wrapper = new TestRecipeWrapper(recipe(30, 201, RecipePropertyStorage.EMPTY));
        wrapper.prepareOverclockPreview(176, 126);
        int buttonCount = wrapper.buttonCount();
        wrapper.overclockPreview.click(0, true);
        wrapper.prepareOverclockPreview(176, 126);
        assertEquals(buttonCount, wrapper.buttonCount());
        assertResult(wrapper.overclockPreview, 30, 201);
        assertFalse(wrapper.overclockPreview.isPerfect());
        assertEquals(GTValues.LV, wrapper.overclockPreview.tier());
        assertTrue(wrapper.previewButton().isHovering(2, 115));
        assertFalse(wrapper.previewButton().isHovering(2, 110));
        assertFalse(wrapper.previewButton().isHovering(170, 5));
    }

    private static RecipeOverclockPreview preview(long eut, int duration) {
        return new RecipeOverclockPreview(recipe(eut, duration, RecipePropertyStorage.EMPTY), false);
    }

    private static Recipe recipe(long eut, int duration, RecipePropertyStorage properties) {
        return new Recipe(Collections.singletonList(new GTRecipeItemInput(new ItemStack(Blocks.STONE, 2))),
                Collections.singletonList(new ItemStack(Blocks.COBBLESTONE)), ChancedOutputList.empty(),
                Collections.emptyList(), Collections.emptyList(), ChancedOutputList.empty(), duration, eut, false,
                false,
                properties, recipeMap.getPrimaryRecipeCategory());
    }

    private static void assertResult(RecipeOverclockPreview preview, long eut, int duration) {
        assertEquals(eut, preview.eut());
        assertEquals(duration, preview.duration());
    }

    private static void assertUnavailable(Recipe recipe, boolean generator, String reason) {
        RecipeOverclockPreview preview = new RecipeOverclockPreview(recipe, generator);
        assertEquals(reason, preview.unavailableReason());
        for (int button = 0; button <= 2; button++) {
            assertFalse(preview.click(button, true));
            assertResult(preview, recipe.getEUt(), recipe.getDuration());
        }
    }

    private static final class TestRecipeWrapper extends GTRecipeWrapper {

        TestRecipeWrapper(Recipe recipe) {
            super(RecipeOverclockPreviewTest.recipeMap, recipe);
        }

        int buttonCount() {
            return buttons.size();
        }

        JeiButton previewButton() {
            return buttons.get(buttons.size() - 1);
        }
    }
}

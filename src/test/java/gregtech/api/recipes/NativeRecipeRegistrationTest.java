package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.api.util.ValidationResult;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeRegistrationTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private Map<ItemAndMetadata, RecyclingData> baseline;
    private boolean invalidBefore;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void capture() {
        baseline = GregTechAPI.RECYCLING_MANAGER.snapshot();
        invalidBefore = RecipeMap.isFoundInvalidRecipe();
        GregTechAPI.RECYCLING_MANAGER.registerRecyclingData(new ItemStack(Items.APPLE),
                new RecyclingData(new MaterialStack(Materials.Iron, 100)));
    }

    @AfterEach
    void restore() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var replacements = new HashMap<ItemAndMetadata, RecyclingData>();
        manager.snapshot().keySet().forEach(key -> replacements.put(key, null));
        replacements.putAll(baseline);
        manager.prepare(manager.revision(), replacements).publish();
        RecipeMap.setFoundInvalidRecipe(invalidBefore);
    }

    @Test
    void receiptIdentifiesTheAcceptedRecipeAndItsComposition() {
        var map = map().build();
        var receipt = recipe(map).buildAndRegisterWithReceipt().orElseThrow();
        assertSame(map, receipt.map());
        assertSame(receipt.recipe(), map.findRecipe(8, List.of(new ItemStack(Items.APPLE, 2)), List.of()));
        assertEquals(200, composition().getMaterials().getFirst().amount);
    }

    @Test
    void rejectedDuplicateDoesNotOverwriteAcceptedComposition() {
        var map = map().build();
        var accepted = recipe(map).buildAndRegisterWithReceipt().orElseThrow();
        var data = composition();
        long revision = GregTechAPI.RECYCLING_MANAGER.revision();
        GregTechAPI.RECYCLING_MANAGER.registerRecyclingData(new ItemStack(Items.APPLE),
                new RecyclingData(new MaterialStack(Materials.Gold, 700)));
        assertTrue(recipe(map).buildAndRegisterWithReceipt().isEmpty());
        assertSame(data, composition());
        assertEquals(revision + 1, GregTechAPI.RECYCLING_MANAGER.revision());
        assertEquals(List.of(accepted.recipe()), map.getRecipeList());
    }

    @Test
    void invalidBuilderAndMapLimitsPublishNeitherRecipeNorComposition() {
        for (int variant = 0; variant < 3; variant++) {
            var map = map().build();
            var builder = recipe(map);
            if (variant == 0) builder.duration(0);
            if (variant == 1) builder.EUt(0);
            if (variant == 2) builder.inputs(new ItemStack(Items.COAL));
            long revision = GregTechAPI.RECYCLING_MANAGER.revision();
            assertTrue(builder.buildAndRegisterWithReceipt().isEmpty());
            assertTrue(map.getRecipeList().isEmpty());
            assertEquals(revision, GregTechAPI.RECYCLING_MANAGER.revision());
        }
    }

    @Test
    void recyclingWithoutDeterministicOutputRejectsWithoutIndexingAnEmptyList() {
        var map = map().build();
        long revision = GregTechAPI.RECYCLING_MANAGER.revision();
        var builder = map.recipeBuilder().inputs(new ItemStack(Items.APPLE))
                .chancedOutput(new ItemStack(Items.DIAMOND), 5000, 0).duration(20).EUt(8).withRecycling();
        assertTrue(builder.buildAndRegisterWithReceipt().isEmpty());
        assertTrue(map.getRecipeList().isEmpty());
        assertEquals(revision, GregTechAPI.RECYCLING_MANAGER.revision());
    }

    @Test
    void buildActionRunsOnceAndCompositionUsesItsFinalInputs() {
        var calls = new AtomicInteger();
        var map = map().itemInputs(2).onBuild(new ResourceLocation("test", "input"), builder -> {
            calls.incrementAndGet();
            builder.inputs(new ItemStack(Items.APPLE, 3));
        }).build();
        var receipt = recipe(map).buildAndRegisterWithReceipt().orElseThrow();
        assertEquals(1, calls.get());
        assertEquals(5, receipt.recipe().getInputs().stream().mapToInt(input -> input.getAmount()).sum());
        assertEquals(500, composition().getMaterials().getFirst().amount);
    }

    @Test
    void mapPostValidationRunsOnceAndItsAcceptedRecipeSuppliesComposition() {
        var calls = new AtomicInteger();
        var map = new RecipeMap<SimpleRecipeBuilder>("native_postvalidate_" + IDS.incrementAndGet(),
                new SimpleRecipeBuilder(), value -> new RecipeMapUI<>(value, false, false, false, false, false),
                2, 1, 0, 0) {

            @Override
            protected ValidationResult<Recipe> postValidateRecipe(ValidationResult<Recipe> candidate) {
                calls.incrementAndGet();
                var modified = new SimpleRecipeBuilder(candidate.getResult(), this)
                        .inputs(new ItemStack(Items.APPLE, 3)).build();
                return super.postValidateRecipe(modified);
            }
        };
        var receipt = recipe(map).buildAndRegisterWithReceipt().orElseThrow();
        assertEquals(1, calls.get());
        assertEquals(5, receipt.recipe().getInputs().stream().mapToInt(input -> input.getAmount()).sum());
        assertEquals(500, composition().getMaterials().getFirst().amount);
    }

    @Test
    void compositionOverflowIsDetectedBeforeRecipeAdmission() {
        var map = map().build();
        GregTechAPI.RECYCLING_MANAGER.registerRecyclingData(new ItemStack(Items.APPLE),
                new RecyclingData(new MaterialStack(Materials.Iron, Long.MAX_VALUE)));
        long revision = GregTechAPI.RECYCLING_MANAGER.revision();
        assertThrows(ArithmeticException.class, () -> recipe(map).buildAndRegisterWithReceipt());
        assertTrue(map.getRecipeList().isEmpty());
        assertEquals(revision, GregTechAPI.RECYCLING_MANAGER.revision());
    }

    private static RecipeMapBuilder<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("native_receipt_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1);
    }

    private static SimpleRecipeBuilder recipe(RecipeMap<SimpleRecipeBuilder> map) {
        return map.recipeBuilder().inputs(new ItemStack(Items.APPLE, 2)).outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(8).withRecycling();
    }

    private static RecyclingData composition() {
        return GregTechAPI.RECYCLING_MANAGER.getRecyclingData(new ItemStack(Items.DIAMOND));
    }
}

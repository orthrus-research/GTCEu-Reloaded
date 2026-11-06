package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.unification.NativeRecyclingCapture;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeRecyclingCaptureTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private final List<ResourceLocation> crafting = new ArrayList<>();
    private Map<ItemAndMetadata, RecyclingData> baseline;
    private NativeRecyclingCapture capture;
    private boolean invalidMachine;
    private boolean invalidCrafting;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void begin() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        baseline = manager.snapshot();
        invalidMachine = RecipeMap.isFoundInvalidRecipe();
        invalidCrafting = ModHandler.hasInvalidRecipe;
        manager.registerRecyclingData(new ItemStack(Items.APPLE),
                new RecyclingData(new MaterialStack(Materials.Iron, 100)));
        capture = manager.beginNativeCapture();
    }

    @AfterEach
    void restore() {
        capture.close();
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var replacement = new HashMap<ItemAndMetadata, RecyclingData>();
        manager.snapshot().keySet().forEach(key -> replacement.put(key, null));
        replacement.putAll(baseline);
        manager.prepare(manager.revision(), replacement).publish();
        RecipeMap.setFoundInvalidRecipe(invalidMachine);
        ModHandler.hasInvalidRecipe = invalidCrafting;
        crafting.forEach(((IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES)::remove);
    }

    @Test
    void capturesAcceptedMachinesAndRetainsUnknownCompositionWithoutRejectedDuplicates() {
        var map = map();
        machine(map, Items.APPLE, Items.DIAMOND).buildAndRegister();
        machine(map, Items.APPLE, Items.EMERALD).buildAndRegister();
        assertTrue(machine(map, Items.COAL, Items.DIAMOND).duration(0).buildAndRegisterWithReceipt().isEmpty());
        GregTechAPI.RECYCLING_MANAGER.removeRecyclingData(new ItemStack(Items.PAPER));
        machine(map, Items.PAPER, Items.EMERALD).buildAndRegister();
        var snapshot = capture.snapshot();
        assertEquals(2, snapshot.size());
        assertEquals(key(Items.DIAMOND), snapshot.getFirst().output());
        assertEquals(Map.of(key(Items.PAPER), 1L), snapshot.getLast().inputs());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test
    void snapshotUsesRecipeIdentityWhenAnEqualLookingRecipeReplacesAnEarlierOne() {
        var map = map();
        var first = machine(map, Items.APPLE, Items.DIAMOND).buildAndRegisterWithReceipt().orElseThrow();
        var before = capture.snapshot();
        assertTrue(map.removeRecipe(first.recipe()));
        machine(map, Items.APPLE, Items.EMERALD).buildAndRegister();
        assertEquals(1, capture.snapshot().size());
        assertEquals(key(Items.EMERALD), capture.snapshot().getFirst().output());
        assertEquals(key(Items.DIAMOND), before.getFirst().output());
    }

    @Test
    void craftingCountsConsumedCellsAndPreservesCallerInputs() {
        String name = craftingName();
        ItemStack input = new ItemStack(Items.APPLE, 9);
        Object[] pattern = { "AAB", 'A', input, 'B', Ingredient.fromStacks(new ItemStack(Items.APPLE)) };
        Object[] before = pattern.clone();
        ModHandler.addShapedRecipe(true, name, new ItemStack(Items.DIAMOND, 2), pattern);
        assertArrayEquals(before, pattern);
        assertEquals(9, input.getCount());
        var contribution = capture.snapshot().getFirst();
        assertEquals(Map.of(key(Items.APPLE), 3L), contribution.inputs());
        assertEquals(2, contribution.outputCount());
        assertEquals(150, GregTechAPI.RECYCLING_MANAGER.getRecyclingData(key(Items.DIAMOND))
                .getMaterials().getFirst().amount);
        assertEquals("crafting", contribution.origin().stage());
    }

    @Test
    void duplicateCraftingAndOverflowDoNotPublishMetadataOrCaptureEntries() {
        String name = craftingName();
        ModHandler.addShapedRecipe(true, name, new ItemStack(Items.DIAMOND), "A", 'A', Items.APPLE);
        var accepted = GregTechAPI.RECYCLING_MANAGER.getRecyclingData(key(Items.DIAMOND));
        ModHandler.addShapedRecipe(true, name, new ItemStack(Items.EMERALD), "A", 'A', Items.APPLE);
        assertEquals(1, capture.snapshot().size());
        assertSame(accepted, GregTechAPI.RECYCLING_MANAGER.getRecyclingData(key(Items.DIAMOND)));
        GregTechAPI.RECYCLING_MANAGER.registerRecyclingData(key(Items.APPLE),
                new RecyclingData(new MaterialStack(Materials.Iron, Long.MAX_VALUE)));
        String overflowing = craftingName();
        long revision = GregTechAPI.RECYCLING_MANAGER.revision();
        assertThrows(ArithmeticException.class, () -> ModHandler.addShapedRecipe(true, overflowing,
                new ItemStack(Items.DIAMOND), "AA", 'A', Items.APPLE));
        assertFalse(ForgeRegistries.RECIPES.containsKey(new ResourceLocation("gregtech", overflowing)));
        assertEquals(revision, GregTechAPI.RECYCLING_MANAGER.revision());
        assertEquals(1, capture.snapshot().size());
    }

    @Test
    void capturesMachineAndCraftingOrderAndExcludesRemovedCraftingRecipes() {
        machine(map(), Items.APPLE, Items.DIAMOND).buildAndRegister();
        String name = craftingName();
        ModHandler.addShapedRecipe(true, name, new ItemStack(Items.COAL), "A", 'A', Items.APPLE);
        machine(map(), Items.APPLE, Items.EMERALD).buildAndRegister();
        assertEquals(List.of(key(Items.DIAMOND), key(Items.COAL), key(Items.EMERALD)),
                capture.snapshot().stream().map(RecyclingContribution::output).toList());
        ModHandler.removeRecipeByName(new ResourceLocation("gregtech", name));
        assertEquals(List.of(key(Items.DIAMOND), key(Items.EMERALD)),
                capture.snapshot().stream().map(RecyclingContribution::output).toList());
    }

    @Test
    void nativeInputRecyclingPolicyIsSharedByInferenceAndCapture() {
        var excluded = new GTRecipeItemInput(new ItemStack(Items.APPLE)) {

            @Override
            public boolean isRecyclingInput() {
                return false;
            }
        };
        assertNull(RecyclingHandler.getRecyclingIngredients(List.of(excluded), 1));
        var map = map();
        map.recipeBuilder().input(excluded).outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8)
                .withRecycling().buildAndRegister();
        assertEquals(1, capture.snapshot().size());
        assertTrue(capture.snapshot().getFirst().inputs().isEmpty());
    }

    @Test
    void inputCachePreservesSpecializedBehaviorWhileStillInterningEquivalentInputs() {
        var cache = GTRecipeInputCache.createCache(16);
        var ordinary = new GTRecipeItemInput(new ItemStack(Items.APPLE));
        var specialized = new GTRecipeItemInput(new ItemStack(Items.APPLE)) {

            @Override
            public boolean isRecyclingInput() {
                return false;
            }
        };
        assertTrue(ordinary.equals(specialized), "The inherited equality alone cannot distinguish these inputs");
        assertSame(ordinary, cache.addOrGet(ordinary));
        assertSame(specialized, cache.addOrGet(specialized));
        assertSame(ordinary, cache.addOrGet(new GTRecipeItemInput(new ItemStack(Items.APPLE))));
        assertEquals(2, cache.size());
    }

    @Test
    void captureLifecycleRejectsNestedOrWrongThreadUseBeforeRecipeAdmission() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        assertThrows(IllegalStateException.class, manager::beginNativeCapture);
        var map = map();
        CompletableFuture.runAsync(() -> {
            assertThrows(IllegalStateException.class, capture::snapshot);
            assertThrows(IllegalStateException.class,
                    () -> machine(map, Items.APPLE, Items.DIAMOND).buildAndRegister());
        }).join();
        assertTrue(map.getRecipeList().isEmpty());
        capture.close();
        capture.close();
        assertFalse(manager.isNativeCaptureActive());
        try (var next = manager.beginNativeCapture()) {
            machine(map, Items.APPLE, Items.DIAMOND).buildAndRegister();
            assertEquals(1, next.snapshot().size());
            assertTrue(capture.snapshot().isEmpty());
        }
    }

    private String craftingName() {
        String name = "native_capture_crafting_" + IDS.incrementAndGet();
        crafting.add(new ResourceLocation("gregtech", name));
        return name;
    }

    private static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("native_capture_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    private static SimpleRecipeBuilder machine(RecipeMap<SimpleRecipeBuilder> map, Item input, Item output) {
        return map.recipeBuilder().inputs(new ItemStack(input)).outputs(new ItemStack(output)).duration(20).EUt(8)
                .withRecycling();
    }

    private static ItemAndMetadata key(Item item) {
        return new ItemAndMetadata(item, 0);
    }
}

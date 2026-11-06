package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecyclingMaterial;
import gregtech.api.recipes.definition.RecyclingRecipeDefinition;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RecyclingOutputSelectionTest {

    private static RecyclingUnificationFixture unification;
    private static List<Material> metals;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.perform();
        metals = List.of(Materials.Copper, Materials.Gold, Materials.Iron, Materials.Lead, Materials.Tin,
                Materials.Zinc);
        var materials = new ArrayList<>(metals);
        materials.addAll(List.of(Materials.Ash, Materials.DarkAsh, Materials.WroughtIron, Materials.AnnealedCopper));
        unification = new RecyclingUnificationFixture(materials,
                List.of(OrePrefix.dust, OrePrefix.dustSmall, OrePrefix.dustTiny, OrePrefix.ingot, OrePrefix.block,
                        OrePrefix.nugget));
    }

    @AfterAll
    static void close() throws Exception {
        unification.close();
    }

    @Test
    void tiedMaterialsUseStableNamesBeforeMaceratorCapacityAndDuration() {
        var expected = metals.stream().sorted(Comparator.comparing(Material::getRegistryName)).limit(4).toList();
        var inputs = new ArrayList<>(
                metals.stream().map(material -> new RecyclingMaterial(material, GTValues.M)).toList());
        for (int seed = 0; seed < 20; seed++) {
            Collections.shuffle(inputs, new Random(seed));
            var recipe = prepare(inputs, null, true, RecipeMaps.MACERATOR_RECIPES);
            assertEquals(expected, materials(recipe));
            assertEquals(expected.stream().mapToLong(Material::getMass).sum(), recipe.getDuration());
            assertEquals(List.of(1, 1, 1, 1), recipe.getOutputs().stream().map(ItemStack::getCount).toList());
        }
    }

    @Test
    void quantityHasPriorityAndAshRemainsLast() {
        var recipe = prepare(List.of(new RecyclingMaterial(Materials.Ash, 10 * GTValues.M),
                new RecyclingMaterial(Materials.Copper, GTValues.M),
                new RecyclingMaterial(Materials.Iron, 2 * GTValues.M),
                new RecyclingMaterial(Materials.DarkAsh, 20 * GTValues.M)), null, true, RecipeMaps.MACERATOR_RECIPES);
        assertEquals(List.of(Materials.Iron, Materials.Copper, Materials.DarkAsh, Materials.Ash), materials(recipe));
    }

    @Test
    void equalFullStacksAreNotCollapsedAndCapacityStillApplies() {
        for (int quantity : List.of(128, 129, 256, 257)) {
            var recipe = prepare(List.of(new RecyclingMaterial(Materials.Iron, quantity * GTValues.M)),
                    null, true, RecipeMaps.MACERATOR_RECIPES);
            assertEquals(Math.min(quantity, 256), recipe.getOutputs().stream().mapToInt(ItemStack::getCount).sum());
            assertTrue(recipe.getOutputs().size() <= 4);
            assertTrue(recipe.getOutputs().stream().allMatch(stack -> stack.getCount() > 0 && stack.getCount() <= 64));
            assertEquals(Math.min(quantity, 256) * Materials.Iron.getMass(), recipe.getDuration());
        }
        var limited = prepare(List.of(new RecyclingMaterial(Materials.Iron, 256 * GTValues.M),
                new RecyclingMaterial(Materials.Copper, GTValues.M)), null, true, RecipeMaps.MACERATOR_RECIPES);
        assertEquals(List.of(Materials.Iron, Materials.Iron, Materials.Iron, Materials.Iron), materials(limited));
    }

    @Test
    void arcCompactionAndRepeatedBlockStacksConserveMaterialWithinCapacity() {
        for (int quantity : List.of(129, 1152, 1153)) {
            var recipe = prepare(List.of(new RecyclingMaterial(Materials.Iron, quantity * GTValues.M)),
                    null, false, RecipeMaps.ARC_FURNACE_RECIPES);
            long recovered = recipe.getOutputs().stream()
                    .mapToLong(stack -> OreDictUnifier.getMaterial(stack).amount * stack.getCount()).sum();
            assertEquals(quantity * GTValues.M, recovered);
            assertTrue(materials(recipe).stream().allMatch(material -> material == Materials.WroughtIron));
            assertTrue(recipe.getOutputs().stream().allMatch(stack -> stack.getCount() > 0 && stack.getCount() <= 64));
            assertEquals(quantity * Materials.WroughtIron.getMass(), recipe.getDuration());
        }
    }

    @Test
    void equalMaterialContributionsCombineBeforeSelectionAndFluidExtraction() {
        var contributions = List.of(new RecyclingMaterial(Materials.Iron, 4 * GTValues.M),
                new RecyclingMaterial(Materials.Iron, 4 * GTValues.M));
        var macerator = prepare(contributions, null, true, RecipeMaps.MACERATOR_RECIPES);
        assertEquals(List.of(Materials.Iron), materials(macerator));
        assertEquals(8, macerator.getOutputs().getFirst().getCount());
        assertEquals(8 * Materials.Iron.getMass(), macerator.getDuration());
        var extractor = prepare(contributions, OrePrefix.cableGtSingle, true, RecipeMaps.EXTRACTOR_RECIPES);
        assertTrue(
                Materials.Iron.getFluid(8 * GTValues.L).isFluidStackIdentical(extractor.getFluidOutputs().getFirst()));
        assertTrue(extractor.getOutputs().isEmpty());
    }

    @Test
    void compoundExtractorPreservesDeclaredMaterialPriority() {
        var first = List.of(new RecyclingMaterial(Materials.Iron, GTValues.M),
                new RecyclingMaterial(Materials.Copper, 2 * GTValues.M));
        var second = new ArrayList<>(first);
        Collections.reverse(second);
        for (var input : List.of(first, second)) {
            var recipe = prepare(input, OrePrefix.cableGtSingle, true, RecipeMaps.EXTRACTOR_RECIPES);
            var primary = input.getFirst();
            assertTrue(primary.material().getFluid((int) (primary.amount() * GTValues.L / GTValues.M))
                    .isFluidStackIdentical(recipe.getFluidOutputs().getFirst()));
            assertEquals(List.of(input.getLast().material()), materials(recipe));
            assertEquals(2 * Materials.Copper.getMass() + Materials.Iron.getMass(), recipe.getDuration());
        }
        for (var input : List.of(List.of(new RecyclingMaterial(Materials.Iron, GTValues.M),
                new RecyclingMaterial(Materials.Copper, GTValues.M)),
                List.of(new RecyclingMaterial(Materials.Copper, GTValues.M),
                        new RecyclingMaterial(Materials.Iron, GTValues.M)))) {
            var recipe = prepare(input, OrePrefix.cableGtSingle, true, RecipeMaps.EXTRACTOR_RECIPES);
            assertTrue(input.getFirst().material().getFluid(GTValues.L)
                    .isFluidStackIdentical(recipe.getFluidOutputs().getFirst()));
        }
    }

    @Test
    void arcSmeltingConvergingMaterialsRemainCombinedAndSorted() {
        var first = List.of(new RecyclingMaterial(Materials.Iron, GTValues.M),
                new RecyclingMaterial(Materials.WroughtIron, GTValues.M),
                new RecyclingMaterial(Materials.Gold, 2 * GTValues.M));
        var reversed = new ArrayList<>(first);
        Collections.reverse(reversed);
        for (var input : List.of(first, reversed)) {
            var recipe = prepare(input, null, false, RecipeMaps.ARC_FURNACE_RECIPES);
            assertEquals(List.of(Materials.Gold, Materials.WroughtIron), materials(recipe));
            assertEquals(List.of(2, 2), recipe.getOutputs().stream().map(ItemStack::getCount).toList());
        }
    }

    private static List<Material> materials(Recipe recipe) {
        return recipe.getOutputs().stream().map(stack -> OreDictUnifier.getMaterial(stack).material).toList();
    }

    private static Recipe prepare(List<RecyclingMaterial> materials, OrePrefix prefix, boolean ignoreArc,
                                  RecipeMap<?> map) {
        var definition = new RecyclingRecipeDefinition(
                new SourceOrigin("java", "output_selection", 1, 1, "case", "postInit"),
                ItemValue.of(new ItemStack(Items.PAPER)), materials, ignoreArc, prefix);
        var result = RecyclingRecipeCompiler.prepare(definition);
        assertTrue(result.value().isPresent(), () -> result.diagnostics().toString());
        return result.value().orElseThrow().stream().filter(value -> value.map() == map).findFirst().orElseThrow()
                .recipe();
    }
}

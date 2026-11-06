package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.AssemblyLineRecipeBuilder;
import gregtech.api.recipes.definition.*;
import gregtech.api.recipes.machines.RecipeMapAssemblyLine;
import gregtech.api.recipes.properties.impl.ComputationProperty;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.TotalComputationProperty;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.api.util.AssemblyLineManager;
import gregtech.common.ConfigHolder;
import gregtech.common.items.MetaItems;
import gregtech.common.items.behaviors.DataItemBehavior;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResearchDefinitionTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final SourceOrigin SOURCE = new SourceOrigin("java", "Research.java", 5, 1, "assembly", "recipes");
    private static ItemValue data;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        var carrier = MetaItems.ITEMS.getFirst();
        var item = carrier.getItem("test.typed.data");
        if (item == null) item = carrier.addItem(31000, "test.typed.data").addComponents(new DataItemBehavior());
        data = ItemValue.of(item.getStackForm());
    }

    @Test
    void scannerAndStationRecipesPublishAndDisappearWithResearchIndex() {
        boolean enabled = ConfigHolder.machines.enableResearch;
        ConfigHolder.machines.enableResearch = true;
        var registrar = new RecipeDefinitionRegistrar();
        var map = map();
        int scannerBaseline = RecipeMaps.SCANNER_RECIPES.getRecipeList().size();
        int stationBaseline = RecipeMaps.RESEARCH_STATION_RECIPES.getRecipeList().size();
        String scannerId = map.unlocalizedName + ":scanner";
        String stationId = map.unlocalizedName + ":station";
        var requirement = new RecipeAttribute.Research(List.of(
                new ResearchRequirement.Scanner(scannerId, data, ItemValue.of(new ItemStack(Items.APPLE)), true, 41,
                        128),
                new ResearchRequirement.Station(stationId, data, ItemValue.of(new ItemStack(Items.CARROT)), false, 4,
                        600, 512)));
        try {
            var declaration = definition(map).withAttributes(requirement);
            var prepared = RecipeDefinitionCompiler.prepare(declaration).value().orElseThrow();
            assertEquals(3, prepared.size());
            assertEquals(scannerBaseline, RecipeMaps.SCANNER_RECIPES.getRecipeList().size());
            assertNull(map.getDataStickEntry(scannerId));
            assertNull(data.stack().getTagCompound(), "preparation cannot mutate the descriptor's data item");
            var result = registrar.replace("research", List.of(declaration));
            assertTrue(result.value().isPresent(), () -> result.diagnostics().toString());
            assertEquals(3, result.value().orElseThrow().recipes());
            Recipe parent = map.getRecipeList().iterator().next();
            assertEquals(List.of(parent), List.copyOf(map.getDataStickEntry(scannerId)));
            assertEquals(List.of(parent), List.copyOf(map.getDataStickEntry(stationId)));
            Recipe scanner = RecipeMaps.SCANNER_RECIPES.findRecipe(128,
                    List.of(data.stack(), new ItemStack(Items.APPLE)), List.of(), true);
            assertNotNull(scanner);
            assertEquals(41, scanner.getDuration());
            assertEquals(scannerId, AssemblyLineManager.readResearchId(scanner.getOutputs().getFirst()));
            Recipe station = RecipeMaps.RESEARCH_STATION_RECIPES.findRecipe(512,
                    List.of(data.stack(), new ItemStack(Items.CARROT)), List.of(), true);
            assertNotNull(station);
            assertEquals(4, station.getProperty(ComputationProperty.getInstance(), -1));
            assertEquals(600, station.getProperty(TotalComputationProperty.getInstance(), -1));
            assertEquals(3, registrar.replace("research", List.of(declaration)).value().orElseThrow().recipes());
            assertEquals(scannerBaseline + 1, RecipeMaps.SCANNER_RECIPES.getRecipeList().size());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            ConfigHolder.machines.enableResearch = enabled;
        }
        assertNull(map.getDataStickEntry(scannerId));
        assertNull(map.getDataStickEntry(stationId));
        assertEquals(scannerBaseline, RecipeMaps.SCANNER_RECIPES.getRecipeList().size());
        assertEquals(stationBaseline, RecipeMaps.RESEARCH_STATION_RECIPES.getRecipeList().size());
    }

    @Test
    void conflictingDerivedRecipeLeavesParentIndexAndOtherRecipesUntouched() {
        var map = map();
        var registrar = new RecipeDefinitionRegistrar();
        var requirement = new RecipeAttribute.Research(List.of(new ResearchRequirement.Scanner(
                map.unlocalizedName, data, ItemValue.of(new ItemStack(Items.POTATO)), true, 20, 32)));
        var declaration = definition(map).withAttributes(requirement);
        var prepared = RecipeDefinitionCompiler.prepare(declaration).value().orElseThrow();
        Recipe child = prepared.getFirst().recipe();
        assertTrue(RecipeMaps.SCANNER_RECIPES.compileRecipe(child));
        try {
            var failure = registrar.replace("conflict", List.of(declaration));
            assertTrue(failure.value().isEmpty());
            assertTrue(map.getRecipeList().isEmpty());
            assertNull(map.getDataStickEntry(map.unlocalizedName));
            assertTrue(RecipeMaps.SCANNER_RECIPES.getRecipeList().stream().anyMatch(recipe -> recipe == child));
        } finally {
            assertTrue(RecipeMaps.SCANNER_RECIPES.removeRecipe(child));
        }
    }

    @Test
    void existingResearchOwnsBothSavedIdSpellingsAndRejectsExternalIndexMutation() {
        var map = map();
        var registrar = new RecipeDefinitionRegistrar();
        String id = map.unlocalizedName + ":xmetaitem.test";
        String alias = id.replace("xmetaitem.", "xitem.meta_item.");
        var declaration = definition(map).withAttributes(new RecipeAttribute.Research(
                List.of(new ResearchRequirement.Existing(id, data))));
        assertEquals(1, registrar.replace("existing", List.of(declaration)).value().orElseThrow().recipes());
        Recipe parent = map.getRecipeList().iterator().next();
        assertEquals(List.of(parent), List.copyOf(map.getDataStickEntry(alias)));
        assertThrows(UnsupportedOperationException.class, () -> map.getDataStickEntry(id).clear());
        assertTrue(map.removeDataStickEntry(id, parent));
        assertTrue(registrar.clear().value().isEmpty());
        assertTrue(map.getRecipeList().stream().anyMatch(recipe -> recipe == parent));
        map.addDataStickEntry(id, parent);
        assertTrue(registrar.clear().value().isPresent());
        assertNull(map.getDataStickEntry(id));
        assertNull(map.getDataStickEntry(alias));
    }

    @Test
    void invalidResearchHasSourceDiagnosticsAndDisabledResearchProducesOnlyParent() {
        var map = map();
        var invalid = definition(map).withAttributes(new RecipeAttribute.Research(List.of(
                new ResearchRequirement.Station("", ItemValue.of(new ItemStack(Items.STICK)),
                        ItemValue.of(ItemStack.EMPTY), true, 5, 2, 0))));
        var result = RecipeDefinitionCompiler.compile(invalid);
        assertTrue(result.value().isEmpty());
        assertTrue(result.diagnostics().stream().allMatch(d -> d.origin().equals(SOURCE)));
        boolean enabled = ConfigHolder.machines.enableResearch;
        try {
            ConfigHolder.machines.enableResearch = false;
            var valid = definition(map).withAttributes(new RecipeAttribute.Research(List.of(
                    new ResearchRequirement.Scanner("disabled", data, ItemValue.of(new ItemStack(Items.APPLE)),
                            true, 20, 32))));
            var prepared = RecipeDefinitionCompiler.prepare(valid).value().orElseThrow();
            assertEquals(1, prepared.size());
            assertNull(prepared.getFirst().recipe().getProperty(ResearchProperty.getInstance(), null));
        } finally {
            ConfigHolder.machines.enableResearch = enabled;
        }
    }

    private static RecipeMapAssemblyLine<AssemblyLineRecipeBuilder> map() {
        var map = new RecipeMapAssemblyLine<>("typed_research_" + IDS.incrementAndGet(),
                new AssemblyLineRecipeBuilder(),
                value -> new RecipeMapUI<>(value, true, true, true, true, false));
        map.onRecipeBuild(new ResourceLocation("test", "research"),
                (RecipeExpansion<AssemblyLineRecipeBuilder>) AssemblyLineManager::expandDefaultResearchRecipes);
        return map;
    }

    @Test
    void registeredDescriptorsWorkInBuiltInDynamicMapsAndFuelMaps() {
        for (RecipeMap<?> map : List.of(RecipeMaps.FURNACE_RECIPES, RecipeMaps.FORMING_PRESS_RECIPES,
                RecipeMaps.CANNER_RECIPES, RecipeMaps.SCANNER_RECIPES, RecipeMaps.RESEARCH_STATION_RECIPES)) {
            var registrar = new RecipeDefinitionRegistrar();
            int baseline = map.getRecipeList().size();
            try {
                var result = registrar.replace("dynamic_map", List.of(definition(map)));
                assertTrue(result.value().isPresent(), () -> map.unlocalizedName + result.diagnostics());
                assertNotNull(map.findRecipe(32, List.of(new ItemStack(Items.BRICK)), List.of(), true));
            } finally {
                assertTrue(registrar.clear().value().isPresent());
            }
            assertEquals(baseline, map.getRecipeList().size());
        }
        var fuel = new RecipeMapBuilder<>("typed_fuel_" + IDS.incrementAndGet(),
                new gregtech.api.recipes.builders.FuelRecipeBuilder()).itemInputs(1).build();
        var definition = new RecipeDefinition(SOURCE, fuel.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.COAL))), List.of(), 20, -32, false);
        var registrar = new RecipeDefinitionRegistrar();
        assertTrue(registrar.replace("fuel", List.of(definition)).value().isPresent());
        assertEquals(-32, fuel.getRecipeList().iterator().next().getEUt());
        assertTrue(registrar.clear().value().isPresent());
    }

    private static RecipeDefinition definition(RecipeMap<?> map) {
        return new RecipeDefinition(SOURCE, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.BRICK))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 20, 32, false);
    }
}

package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.*;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CraftingOwnershipTest {

    private static final AtomicInteger IDS = new AtomicInteger(4000);
    private static RecyclingUnificationFixture unification;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.perform();
        unification = new RecyclingUnificationFixture();
    }

    @AfterAll
    static void close() throws Exception {
        unification.close();
    }

    @Test
    void acceptedAuthoringStateCanBeRevisedWithoutLeakingFailedOrClearedDeclarations() throws Exception {
        var registrar = new RecipeDefinitionRegistrar();
        var name = name();
        var added = name();
        var shaped = new ShapedCraftingDefinition(origin(), name, "", ItemValue.of(new ItemStack(Items.DIAMOND)),
                2, 1, true, Arrays.asList(ItemIngredient.exact(new ItemStack(Items.PAPER)), null));
        var declarations = new ArrayList<CraftingMutation>(List.of(shaped));
        assertTrue(registrar.craftingGroup("craft").isEmpty());
        try {
            assertTrue(registrar.replaceCrafting("craft", declarations).value().isPresent());
            var accepted = registrar.craftingGroup("craft").orElseThrow();
            declarations.clear();
            assertEquals(List.of(shaped), accepted.declarations());
            assertNull(((ShapedCraftingDefinition) accepted.declarations().getFirst()).inputs().get(1));
            assertThrows(UnsupportedOperationException.class, () -> accepted.declarations().clear());
            assertThrows(UnsupportedOperationException.class, () -> accepted.contributions().clear());
            assertThrows(UnsupportedOperationException.class, () -> accepted.recovery().clear());
            assertTrue(registrar.replaceCrafting("craft", List.of(shaped, shaped)).value().isEmpty());
            assertSame(accepted, registrar.craftingGroup("craft").orElseThrow());

            var revised = new ArrayList<>(accepted.declarations());
            revised.add(recipe(added));
            assertTrue(registrar.replaceCrafting("craft", revised, accepted.contributions(), accepted.recovery(),
                    List.of()).value().isPresent());
            assertEquals(2, registrar.craftingGroup("craft").orElseThrow().declarations().size());
            assertEquals(1, accepted.declarations().size(), "Earlier values remain immutable snapshots");
            assertTrue(registrar.replaceCrafting("craft", List.of()).value().isPresent());
            assertTrue(registrar.craftingGroup("craft").orElseThrow().declarations().isEmpty());
            assertTrue(ForgeRegistries.RECIPES.getValue(name).isDynamic());
            assertTrue(ForgeRegistries.RECIPES.getValue(added).isDynamic());

            var read = new java.util.concurrent.FutureTask<>(() -> registrar.craftingGroup("craft"));
            Thread.ofPlatform().start(read).join();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(java.util.concurrent.ExecutionException.class, read::get).getCause());
            assertTrue(registrar.clear().value().isPresent());
            assertTrue(registrar.craftingGroup("craft").isEmpty());
            assertFalse(ForgeRegistries.RECIPES.containsKey(name));
            assertFalse(ForgeRegistries.RECIPES.containsKey(added));
            assertEquals(List.of(shaped), accepted.declarations());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void replacementDisableReenableAndClearPreserveOriginalsAndOutsideRecipes() {
        var registries = CraftingRegistryTransaction.currentRegistries();
        var images = registries.stream().map(CraftingRegistryImage::new).toList();
        var existing = name();
        var added = name();
        var outside = name();
        var seed = publish(existing);
        var original = ForgeRegistries.RECIPES.getValue(existing);
        var registrar = new RecipeDefinitionRegistrar();
        CraftingRegistryTransaction foreign = null;
        try {
            assertTrue(
                    registrar.replaceCrafting("craft", List.of(recipe(existing), recipe(added))).value().isPresent());
            var addedRecipe = ForgeRegistries.RECIPES.getValue(added);
            assertNotSame(original, ForgeRegistries.RECIPES.getValue(existing));
            assertTrue(registrar.replaceCrafting("craft", List.of(new DisabledCraftingDefinition(origin(), existing)))
                    .value().isPresent());
            assertTrue(ForgeRegistries.RECIPES.getValue(existing).isDynamic());
            assertTrue(ForgeRegistries.RECIPES.getValue(added).isDynamic(), "Omitted prior name stays disabled");
            assertTrue(
                    registrar.replaceCrafting("craft", List.of(recipe(existing), recipe(added))).value().isPresent());
            assertFalse(ForgeRegistries.RECIPES.getValue(existing).isDynamic());
            assertNotSame(addedRecipe, ForgeRegistries.RECIPES.getValue(added));
            assertTrue(registrar.replaceCrafting("craft", List.of()).value().isPresent());
            assertTrue(ForgeRegistries.RECIPES.getValue(existing).isDynamic());
            foreign = publish(outside);
            var foreignRecipe = ForgeRegistries.RECIPES.getValue(outside);
            assertTrue(registrar.clear().value().isPresent());
            assertSame(original, ForgeRegistries.RECIPES.getValue(existing));
            assertFalse(ForgeRegistries.RECIPES.containsKey(added));
            assertSame(foreignRecipe, ForgeRegistries.RECIPES.getValue(outside));
            assertTrue(registrar.groups().isEmpty());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            if (foreign != null) foreign.restoration().prepare().publish();
            seed.restoration().prepare().publish();
        }
        for (int i = 0; i < registries.size(); i++) assertTrue(images.get(i).matches(registries.get(i)));
    }

    @Test
    void sharedClaimsRejectOtherOwnersAndGroupsWhileAbsentRemovalClaimsNothing() {
        var first = new RecipeDefinitionRegistrar();
        var second = new RecipeDefinitionRegistrar();
        var name = name();
        var absent = name();
        try {
            assertTrue(first.replaceCrafting("first", List.of(recipe(name))).value().isPresent());
            var original = ForgeRegistries.RECIPES.getValue(name);
            var conflict = second.replaceCrafting("second", List.of(recipe(name)));
            assertEquals("crafting.owner", conflict.diagnostics().getFirst().code());
            assertSame(original, ForgeRegistries.RECIPES.getValue(name));
            assertTrue(first.replaceCrafting("another", List.of(recipe(name))).value().isEmpty());
            assertTrue(second.replaceCrafting("noop", List.of(new DisabledCraftingDefinition(origin(), absent)))
                    .value().isPresent());
            assertFalse(ForgeRegistries.RECIPES.containsKey(absent));
            assertTrue(first.replaceCrafting("new", List.of(recipe(absent))).value().isPresent());
            assertTrue(first.clear().value().isPresent());
            assertTrue(second.replaceCrafting("second", List.of(recipe(name))).value().isPresent());
            assertTrue(second.replace("second", List.of()).value().isEmpty());
            assertTrue(second.replaceRecovery("second", List.of(), List.of(), List.of()).value().isEmpty());
        } finally {
            assertTrue(first.clear().value().isPresent());
            assertTrue(second.clear().value().isPresent());
        }
    }

    @Test
    void invalidAndDuplicateDeclarationsPublishNothingAndDoNotClaimNames() {
        var registrar = new RecipeDefinitionRegistrar();
        var other = new RecipeDefinitionRegistrar();
        var name = name();
        var invalidName = name();
        var invalid = new ShapelessCraftingDefinition(origin(), invalidName, "", ItemValue.of(ItemStack.EMPTY),
                List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
        try {
            var result = registrar.replaceCrafting("bad", List.of(recipe(name), invalid));
            assertEquals("crafting.output.empty", result.diagnostics().getFirst().code());
            assertFalse(ForgeRegistries.RECIPES.containsKey(name));
            assertFalse(ForgeRegistries.RECIPES.containsKey(invalidName));
            result = registrar.replaceCrafting("duplicate", List.of(recipe(name),
                    new DisabledCraftingDefinition(origin(), name)));
            assertEquals("crafting.declaration.identity", result.diagnostics().getFirst().code());
            assertTrue(registrar.groups().isEmpty());
            assertTrue(other.replaceCrafting("accepted", List.of(recipe(name))).value().isPresent());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            assertTrue(other.clear().value().isPresent());
        }
    }

    @Test
    void craftingCompositionAndRecoveryReplaceDisableAndRestoreTogether() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var output = input();
        var baselineData = new RecyclingData(new MaterialStack(Materials.Copper, GTValues.M));
        manager.registerRecyclingData(output, baselineData);
        var baseline = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(output).outputs(new ItemStack(Items.COAL))
                .duration(20).EUt(8).buildAndRegisterWithReceipt().orElseThrow();
        var name = name();
        var seed = publish(name);
        var original = ForgeRegistries.RECIPES.getValue(name);
        var registrar = new RecipeDefinitionRegistrar();
        try {
            assertTrue(registrar.replaceCrafting("combined", List.of(recipe(name)),
                    List.of(fixed(output, 2 * GTValues.M)), List.of(policy(output)), List.of(baseline)).value()
                    .isPresent());
            assertEquals(2, find(output).getOutputs().getFirst().getCount());
            var accepted = registrar.craftingGroup("combined").orElseThrow();
            assertEquals(1, accepted.contributions().size());
            assertEquals(1, accepted.recovery().size());
            var previousCrafting = ForgeRegistries.RECIPES.getValue(name);
            var previousRecovery = find(output);
            var previousData = manager.getRecyclingData(output);
            var invalid = registrar.replaceCrafting("combined", List.of(new DisabledCraftingDefinition(origin(), name)),
                    List.of(fixed(output, Long.MAX_VALUE)), List.of(policy(output)), List.of());
            assertTrue(invalid.value().isEmpty());
            assertSame(accepted, registrar.craftingGroup("combined").orElseThrow());
            assertSame(previousCrafting, ForgeRegistries.RECIPES.getValue(name));
            assertSame(previousRecovery, find(output));
            assertSame(previousData, manager.getRecyclingData(output));
            assertTrue(registrar.replaceCrafting("combined", List.of()).value().isPresent());
            assertTrue(ForgeRegistries.RECIPES.getValue(name).isDynamic());
            assertNull(find(output));
            assertSame(baselineData, manager.getRecyclingData(output));
            assertTrue(registrar.replaceCrafting("combined", List.of(recipe(name)),
                    List.of(fixed(output, GTValues.M)), List.of(policy(output)), List.of()).value().isPresent());
            assertEquals(1, find(output).getOutputs().getFirst().getCount());
            assertTrue(registrar.clear().value().isPresent());
            assertSame(original, ForgeRegistries.RECIPES.getValue(name));
            assertSame(baseline.recipe(), find(output));
            assertSame(baselineData, manager.getRecyclingData(output));
        } finally {
            assertTrue(registrar.clear().value().isPresent());
            seed.restoration().prepare().publish();
            baseline.map().removeRecipe(baseline.recipe());
            restoreMetadata(before);
        }
    }

    @Test
    void outsideCraftingConflictPreventsClearOfDirectAndRecoveryGroups() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var output = input();
        var directInput = input();
        var name = name();
        var registrar = new RecipeDefinitionRegistrar();
        CraftingRegistryTransaction conflict = null;
        try {
            assertTrue(registrar.replaceCrafting("craft", List.of(recipe(name)), List.of(fixed(output, GTValues.M)),
                    List.of(policy(output)), List.of()).value().isPresent());
            assertTrue(registrar.replace("direct", List.of(new RecipeDefinition(origin(),
                    RecipeMaps.MACERATOR_RECIPES.unlocalizedName, List.of(ItemIngredient.exact(directInput)),
                    List.of(ItemValue.of(new ItemStack(Items.COAL))), 20, 8, false))).value().isPresent());
            var direct = find(directInput);
            var recovery = find(output);
            var data = manager.getRecyclingData(output);
            conflict = publish(name);
            var foreign = ForgeRegistries.RECIPES.getValue(name);
            var accepted = registrar.craftingGroup("craft").orElseThrow();
            assertTrue(registrar.clear().value().isEmpty());
            assertSame(accepted, registrar.craftingGroup("craft").orElseThrow());
            assertEquals(2, registrar.groups().size());
            assertSame(foreign, ForgeRegistries.RECIPES.getValue(name));
            assertSame(direct, find(directInput));
            assertSame(recovery, find(output));
            assertSame(data, manager.getRecyclingData(output));
            conflict.restore();
            conflict = null;
            assertTrue(registrar.clear().value().isPresent());
            assertNull(find(directInput));
            assertNull(find(output));
            assertFalse(ForgeRegistries.RECIPES.containsKey(name));
        } finally {
            if (conflict != null) conflict.restore();
            assertTrue(registrar.clear().value().isPresent());
            restoreMetadata(before);
        }
    }

    @Test
    void preparedClaimsAreStaleAfterAnotherOwnerCommits() {
        var first = new RecipeCraftingOwnership();
        var second = new RecipeCraftingOwnership();
        var firstName = name();
        var secondName = name();
        var pending = first.prepare("first", Map.of(firstName,
                CraftingDefinitionCompiler.compile(recipe(firstName)).value().orElseThrow()), origin());
        var accepted = second.prepare("second", Map.of(secondName,
                CraftingDefinitionCompiler.compile(recipe(secondName)).value().orElseThrow()), origin());
        accepted.publish();
        accepted.commitOwnership();
        try {
            assertFalse(pending.isCurrent());
            assertThrows(IllegalStateException.class, pending::publish);
            assertFalse(ForgeRegistries.RECIPES.containsKey(firstName));
            assertTrue(ForgeRegistries.RECIPES.containsKey(secondName));
        } finally {
            var clear = second.prepareClear();
            clear.publish();
            clear.commitOwnership();
        }
    }

    @Test
    void craftingChangesRecomputeRecoveryOwnedByAnotherRegistrar() {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var before = manager.snapshot();
        var intermediate = input();
        var output = input();
        var name = name();
        var upstream = new RecipeDefinitionRegistrar();
        var downstream = new RecipeDefinitionRegistrar();
        var dependency = new RecyclingContribution(origin(), new ItemAndMetadata(output), 1,
                Map.of(new ItemAndMetadata(intermediate), 2L), Map.of(), RecyclingRoutePolicy.MINIMUM_INPUTS);
        try {
            assertTrue(upstream.replaceCrafting("craft", List.of(recipe(name)),
                    List.of(fixed(intermediate, GTValues.M)), List.of(), List.of()).value().isPresent());
            assertTrue(downstream.replaceRecovery("dependent", List.of(dependency), List.of(policy(output)), List.of())
                    .value().isPresent());
            assertEquals(2, find(output).getOutputs().getFirst().getCount());
            assertTrue(upstream.replaceCrafting("craft", List.of(recipe(name)),
                    List.of(fixed(intermediate, 2 * GTValues.M)), List.of(), List.of()).value().isPresent());
            assertEquals(4, find(output).getOutputs().getFirst().getCount());
            var previous = find(output);
            var forbidden = downstream.replaceCrafting("steal", List.of(recipe(name)),
                    List.of(fixed(input(), GTValues.M)), List.of(), List.of());
            assertEquals("crafting.owner", forbidden.diagnostics().getFirst().code());
            assertSame(previous, find(output), "Failed crafting ownership must not republish downstream recipes");
            assertEquals(List.of("dependent"), downstream.groups());
            assertTrue(upstream.replaceCrafting("craft", List.of()).value().isPresent());
            assertNull(find(output));
            assertTrue(ForgeRegistries.RECIPES.getValue(name).isDynamic());
            assertTrue(upstream.replaceCrafting("craft", List.of(recipe(name)),
                    List.of(fixed(intermediate, GTValues.M)), List.of(), List.of()).value().isPresent());
            assertEquals(2, find(output).getOutputs().getFirst().getCount());
        } finally {
            assertTrue(upstream.clear().value().isPresent());
            assertTrue(downstream.clear().value().isPresent());
            restoreMetadata(before);
        }
    }

    private static ShapelessCraftingDefinition recipe(ResourceLocation name) {
        return new ShapelessCraftingDefinition(origin(), name, "", ItemValue.of(new ItemStack(Items.DIAMOND)),
                List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
    }

    private static CraftingRegistryTransaction publish(ResourceLocation name) {
        var transaction = CraftingRegistryTransaction.prepare(Map.of(name,
                CraftingDefinitionCompiler.compile(recipe(name)).value().orElseThrow()));
        transaction.publish();
        return transaction;
    }

    private static ResourceLocation name() {
        return new ResourceLocation("crafting_ownership_test", "recipe_" + IDS.incrementAndGet());
    }

    private static SourceOrigin origin() {
        return new SourceOrigin("java", "CraftingOwnershipTest", 1, 1, "craft", "postInit");
    }

    private static ItemStack input() {
        return new ItemStack(Items.PAPER, 1, IDS.incrementAndGet());
    }

    private static Recipe find(ItemStack input) {
        return RecipeMaps.MACERATOR_RECIPES.findRecipe(Long.MAX_VALUE, List.of(input), List.of());
    }

    private static RecyclingContribution fixed(ItemStack output, long amount) {
        return new RecyclingContribution(origin(), new ItemAndMetadata(output), 1, Map.of(),
                Map.of(Materials.Iron, amount), RecyclingRoutePolicy.MINIMUM_INPUTS);
    }

    private static RecyclingRecoveryDefinition policy(ItemStack output) {
        return new RecyclingRecoveryDefinition(origin(), ItemValue.of(output), true, null);
    }

    private static void restoreMetadata(Map<ItemAndMetadata, RecyclingData> before) {
        var manager = GregTechAPI.RECYCLING_MANAGER;
        var replacements = new HashMap<ItemAndMetadata, RecyclingData>();
        manager.snapshot().keySet().forEach(key -> replacements.put(key, null));
        replacements.putAll(before);
        manager.prepare(manager.revision(), replacements).publish();
    }
}

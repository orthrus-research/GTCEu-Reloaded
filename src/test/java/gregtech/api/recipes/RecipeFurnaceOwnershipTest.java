package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.FurnaceRecipeDefinition;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecipeDefinition;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import groovy.lang.GroovyShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFurnaceOwnershipTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final SourceOrigin SOURCE = new SourceOrigin("java", "Baking.java", 12, 4, "baking", "recipes");
    private final RecipeDefinitionRegistrar owner = new RecipeDefinitionRegistrar();
    private Map<ItemStack, ItemStack> recipes;
    private Map<ItemStack, Float> experience;
    private Map<ItemStack, ItemStack> baselineRecipes;
    private Map<ItemStack, Float> baselineExperience;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void isolateFurnaceTables() {
        recipes = FurnaceRecipes.instance().getSmeltingList();
        experience = FurnaceRecipes.instance().experienceList;
        baselineRecipes = new IdentityHashMap<>(recipes);
        baselineExperience = new IdentityHashMap<>(experience);
        recipes.clear();
        experience.clear();
    }

    @AfterEach
    void restoreFurnaceTables() {
        recipes.clear();
        recipes.putAll(baselineRecipes);
        experience.clear();
        experience.putAll(baselineExperience);
    }

    @Test
    void descriptorsSnapshotStacksAndValidateVanillaMatchingLimits() {
        var input = new ItemStack(Items.APPLE, 5);
        var output = new ItemStack(Items.BREAD, 2);
        var tag = new NBTTagCompound();
        tag.setInteger("flavor", 7);
        output.setTagCompound(tag);
        var definition = FurnaceRecipeDefinition.of(input, output, -0.0f);
        assertEquals(5, input.getCount());
        assertEquals(1, definition.input().stack().getCount());
        assertEquals(0, Float.floatToIntBits(definition.experience()));
        output.getTagCompound().setInteger("flavor", 8);
        output.setCount(6);
        definition.output().stack().getTagCompound().setInteger("flavor", 9);
        assertEquals(7, definition.output().stack().getTagCompound().getInteger("flavor"));
        assertEquals(2, definition.output().stack().getCount());
        input.setTagCompound(new NBTTagCompound());
        assertThrows(IllegalArgumentException.class, () -> FurnaceRecipeDefinition.of(input, output, 0));
        assertThrows(IllegalArgumentException.class, () -> effect(ItemStack.EMPTY, output, 0));
        assertThrows(IllegalArgumentException.class, () -> effect(new ItemStack(Items.APPLE), ItemStack.EMPTY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD, 65), 0));
        assertThrows(IllegalArgumentException.class,
                () -> effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD, 1, GTValues.W), 0));
        for (float invalid : new float[] { -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class,
                    () -> effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), invalid));
        }
    }

    @Test
    void preparationIsPureAndRepeatedReplaceAndClearLeaveNoFurnaceState() {
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD, 2), .5f));
        var declaration = definition(map);
        success(RecipeDefinitionCompiler.compile(declaration));
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
        assertTrue(map.getRecipeList().isEmpty());
        for (int i = 0; i < 3; i++) {
            success(owner.replace("baking", List.of(declaration)));
            assertEquals(1, recipes.size());
            assertEquals(1, experience.size());
            assertEquals(1, map.getRecipeList().size());
            var result = FurnaceRecipes.instance().getSmeltingResult(new ItemStack(Items.APPLE));
            assertEquals(Items.BREAD, result.getItem());
            assertEquals(2, result.getCount());
            assertEquals(.5f, FurnaceRecipes.instance().getSmeltingExperience(result));
        }
        success(owner.clear());
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
        assertTrue(map.getRecipeList().isEmpty());
        assertSame(recipes, FurnaceRecipes.instance().getSmeltingList());
        assertSame(experience, FurnaceRecipes.instance().experienceList);
    }

    @Test
    void failedFurnaceReplacementPreservesPreviousMachineRecipeAndExperience() {
        var first = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        success(owner.replace("baking", List.of(definition(first))));
        Recipe machine = first.getRecipeList().iterator().next();
        var before = new FurnaceRegistrySnapshot(recipes, experience);
        var conflicting = map(effect(new ItemStack(Items.CARROT), new ItemStack(Items.BREAD), .75f));
        // Another group owns the shared output at a different requested XP.
        var failure = owner.replace("other", List.of(definition(conflicting)));
        failed(failure, "recipe.furnace.conflict");
        assertTrue(before.isCurrent());
        assertTrue(conflicting.getRecipeList().isEmpty());
        assertSame(machine, first.getRecipeList().iterator().next());
        ItemStack outsideInput = new ItemStack(Items.CARROT);
        recipes.put(outsideInput, new ItemStack(Items.COAL));
        var replacement = map(effect(new ItemStack(Items.CARROT), new ItemStack(Items.DIAMOND), 1));
        before = new FurnaceRegistrySnapshot(recipes, experience);
        failed(owner.replace("baking", List.of(definition(replacement))), "recipe.furnace.conflict");
        assertTrue(before.isCurrent());
        assertSame(machine, first.getRecipeList().iterator().next());
        assertTrue(replacement.getRecipeList().isEmpty());
        success(owner.clear());
        assertEquals(1, recipes.size());
        assertTrue(recipes.containsKey(outsideInput));
        assertTrue(experience.isEmpty());
    }

    @Test
    void machineConflictNeverPublishesItsFurnaceEffects() {
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        map.recipeBuilder().inputs(ItemIngredient.exact(new ItemStack(Items.APPLE)).compile())
                .outputs(new ItemStack(Items.DIAMOND))
                .duration(20).EUt(8).ignoreAllBuildActions().buildAndRegister();
        failed(owner.replace("baking", List.of(definition(map))), "recipe.index.conflict");
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
        assertEquals(1, map.getRecipeList().size());
    }

    @Test
    void wildcardInputsConflictInBothDirections() {
        for (boolean existingWildcard : List.of(false, true)) {
            ItemStack outside = new ItemStack(Items.DYE, 1, existingWildcard ? GTValues.W : 2);
            recipes.put(outside, new ItemStack(Items.COAL));
            var map = map(effect(new ItemStack(Items.DYE, 1, existingWildcard ? 2 : GTValues.W),
                    new ItemStack(Items.BREAD), .5f));
            failed(owner.replace("baking", List.of(definition(map))), "recipe.furnace.conflict");
            assertTrue(map.getRecipeList().isEmpty());
            assertEquals(1, recipes.size());
            assertTrue(experience.isEmpty());
            recipes.remove(outside);
        }
    }

    @Test
    void sameOutputGroupsShareExperienceUntilTheLastOwnerIsRemoved() {
        var first = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        var second = map(effect(new ItemStack(Items.CARROT), new ItemStack(Items.BREAD, 3), .5f));
        success(owner.replace("first", List.of(definition(first))));
        ItemStack xpKey = experience.keySet().iterator().next();
        success(owner.replace("second", List.of(definition(second))));
        assertEquals(2, recipes.size());
        assertEquals(1, experience.size());
        assertSame(xpKey, experience.keySet().iterator().next());
        success(owner.replace("first", List.of()));
        assertEquals(1, recipes.size());
        assertSame(xpKey, experience.keySet().iterator().next());
        assertEquals(.5f, FurnaceRecipes.instance().getSmeltingExperience(new ItemStack(Items.BREAD)));
        success(owner.clear());
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
    }

    @Test
    void existingCompatibleExperienceIsBorrowedAndPreservedButConflictsAreRejected() {
        ItemStack key = new ItemStack(Items.BREAD);
        experience.put(key, .5f);
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        success(owner.replace("baking", List.of(definition(map))));
        assertEquals(1, experience.size());
        assertSame(key, experience.keySet().iterator().next());
        success(owner.clear());
        assertEquals(.5f, experience.get(key));
        experience.put(key, 1f);
        var before = new FurnaceRegistrySnapshot(recipes, experience);
        failed(owner.replace("baking", List.of(definition(map))), "recipe.furnace.conflict");
        assertTrue(before.isCurrent());
        assertTrue(map.getRecipeList().isEmpty());
    }

    @Test
    void outsideProducerCannotAcquireOrLoseExperienceAsASideEffect() {
        ItemStack foreign = new ItemStack(Items.CARROT);
        recipes.put(foreign, new ItemStack(Items.BREAD));
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        failed(owner.replace("baking", List.of(definition(map))), "recipe.furnace.conflict");
        recipes.remove(foreign);
        success(owner.replace("baking", List.of(definition(map))));
        recipes.put(foreign, new ItemStack(Items.BREAD));
        ItemStack xpKey = experience.keySet().iterator().next();
        // A foreign recipe now relies on the owned XP. Clear must not wedge: it releases the claim and keeps the value.
        success(owner.clear());
        assertTrue(map.getRecipeList().isEmpty());
        assertEquals(1, recipes.size());
        assertTrue(recipes.containsKey(foreign));
        assertEquals(.5f, experience.get(xpKey));
        assertEquals(.5f, FurnaceRecipes.instance().getSmeltingExperience(new ItemStack(Items.BREAD)));
        // The released entry is no longer owned, so later clears neither fail nor remove it.
        success(owner.clear());
        recipes.remove(foreign);
        success(owner.clear());
        assertEquals(.5f, experience.get(xpKey));
        // A replacement that disagrees with the retained value still conflicts instead of rewriting foreign XP.
        recipes.put(foreign, new ItemStack(Items.BREAD));
        var different = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .75f));
        failed(owner.replace("baking", List.of(definition(different))), "recipe.furnace.conflict");
        assertEquals(.5f, experience.get(xpKey));
    }

    @Test
    void replacementNeverReleasesExperienceAForeignProducerRelies() {
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        success(owner.replace("baking", List.of(definition(map))));
        ItemStack xpKey = experience.keySet().iterator().next();
        ItemStack foreign = new ItemStack(Items.CARROT);
        recipes.put(foreign, new ItemStack(Items.BREAD));
        // Only clear() releases a relied-on claim. An empty replacement would change the foreign XP, so it refuses.
        failed(owner.replace("baking", List.of()), "recipe.furnace.conflict");
        assertEquals(2, recipes.size());
        assertEquals(.5f, experience.get(xpKey));
        // Re-declaring the same XP keeps it owned rather than silently adopting the entry as foreign.
        var same = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        success(owner.replace("baking", List.of(definition(same))));
        assertEquals(.5f, experience.get(xpKey));
        recipes.remove(foreign);
        success(owner.clear());
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty(), "the re-declared XP must still be owned and removed by clear()");
    }

    @Test
    void outsideEditsToOwnedStacksAndExperienceAreNeverOverwritten() {
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        success(owner.replace("baking", List.of(definition(map))));
        ItemStack input = recipes.keySet().iterator().next();
        ItemStack output = recipes.get(input);
        ItemStack xpKey = experience.keySet().iterator().next();
        output.setCount(2);
        failed(owner.clear(), "recipe.furnace.conflict");
        assertEquals(2, recipes.get(input).getCount());
        output.setCount(1);
        experience.put(xpKey, .75f);
        failed(owner.clear(), "recipe.furnace.conflict");
        assertEquals(.75f, experience.get(xpKey));
        experience.remove(xpKey);
        failed(owner.clear(), "recipe.furnace.conflict");
        experience.put(xpKey, .5f);
        input.setCount(2);
        failed(owner.clear(), "recipe.furnace.conflict");
        input.setCount(1);
        xpKey.setCount(2);
        failed(owner.clear(), "recipe.furnace.conflict");
        xpKey.setCount(1);
        ItemStack overlapping = input.copy();
        recipes.put(overlapping, new ItemStack(Items.COAL));
        failed(owner.clear(), "recipe.furnace.conflict");
        recipes.remove(overlapping);
        success(owner.clear());
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
    }

    @Test
    void itemExperienceOverridesMustAgreeWithTheDescriptor() {
        Item output = new Item() {

            @Override
            public float getSmeltingExperience(ItemStack stack) {
                return 2;
            }
        };
        var bad = map(effect(new ItemStack(Items.APPLE), new ItemStack(output), .5f));
        failed(owner.replace("baking", List.of(definition(bad))), "recipe.furnace.conflict");
        var good = map(effect(new ItemStack(Items.APPLE), new ItemStack(output), 2));
        success(owner.replace("baking", List.of(definition(good))));
        assertEquals(2, FurnaceRecipes.instance().getSmeltingExperience(new ItemStack(output)));
        success(owner.clear());
    }

    @Test
    void itemHooksCannotInvalidatePreparedMachineIndexesOrFurnaceTables() {
        var affected = map();
        AtomicBoolean mutate = new AtomicBoolean(true);
        Item output = new Item() {

            @Override
            public float getSmeltingExperience(ItemStack stack) {
                if (mutate.getAndSet(false)) affected.recipeBuilder().inputs(new ItemStack(Items.CARROT))
                        .outputs(new ItemStack(Items.DIAMOND)).duration(20).EUt(8)
                        .ignoreAllBuildActions().buildAndRegister();
                return -1;
            }
        };
        addExpansion(affected, effect(new ItemStack(Items.APPLE), new ItemStack(output), .5f));
        failed(owner.replace("baking", List.of(definition(affected))), "recipe.registry.changed");
        assertEquals(1, affected.getRecipeList().size());
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
        ItemStack outside = new ItemStack(Items.CARROT);
        Item other = new Item() {

            @Override
            public float getSmeltingExperience(ItemStack stack) {
                recipes.put(outside, new ItemStack(Items.COAL));
                return -1;
            }
        };
        var second = map(effect(new ItemStack(Items.APPLE), new ItemStack(other), .5f));
        failed(owner.replace("baking", List.of(definition(second))), "recipe.furnace.publish");
        assertEquals(1, recipes.size());
        assertTrue(recipes.containsKey(outside));
        assertTrue(second.getRecipeList().isEmpty());
        assertTrue(experience.isEmpty());
    }

    @Test
    void publicationFailureRestoresBothTablesAndPreservesUnchangedReferences() {
        AtomicBoolean fail = new AtomicBoolean();
        Map<ItemStack, Float> throwingExperience = new IdentityHashMap<>() {

            @Override
            public Float put(ItemStack key, Float value) {
                if (fail.getAndSet(false)) throw new IllegalArgumentException("injected publication failure");
                return super.put(key, value);
            }
        };
        ItemStack input = new ItemStack(Items.CARROT);
        ItemStack output = new ItemStack(Items.COAL);
        recipes.put(input, output);
        throwingExperience.put(output, .25f);
        var snapshot = new FurnaceRegistrySnapshot(recipes, throwingExperience);
        Map<ItemStack, ItemStack> next = new IdentityHashMap<>();
        ItemStack added = new ItemStack(Items.BREAD);
        next.put(new ItemStack(Items.APPLE), added);
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> snapshot.publish(next, Map.of(added, .5f)));
        assertTrue(snapshot.isCurrent());
        assertSame(output, recipes.get(input));
        assertEquals(.25f, throwingExperience.get(output));
    }

    @Test
    void replacementRollsBackPublishedFurnaceTablesOnAnyRuntimeFailure() {
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        owner.publicationProbe(stage -> {
            if (stage.equals("furnace.published")) throw new UnsupportedOperationException("injected");
        });
        failed(owner.replace("baking", List.of(definition(map))), "recipe.registry.changed");
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
        assertTrue(map.getRecipeList().isEmpty());
        assertTrue(owner.groups().isEmpty());
        owner.publicationProbe(stage -> {});
        success(owner.replace("baking", List.of(definition(map))));
        assertEquals(1, recipes.size());
    }

    @Test
    void clearRestoresEveryPublishedParticipantEvenWhenOneRestoreFails() {
        var map = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        success(owner.replace("baking", List.of(definition(map))));
        ItemStack input = recipes.keySet().iterator().next();
        ItemStack xpKey = experience.keySet().iterator().next();
        // A non-IllegalStateException failure after the furnace published must restore it, not escape.
        owner.publicationProbe(stage -> {
            if (stage.equals("furnace.published")) throw new ArithmeticException("injected");
        });
        failed(owner.clear(), "recipe.registry.changed");
        assertSame(input, recipes.keySet().iterator().next());
        assertEquals(.5f, experience.get(xpKey));
        assertEquals(1, map.getRecipeList().size());
        // When the crafting restore itself fails, the furnace restore still runs and the diagnostic says so.
        owner.publicationProbe(stage -> {
            if (stage.equals("crafting.published")) throw new UnsupportedOperationException("injected publish");
            if (stage.equals("crafting.restore")) throw new ArithmeticException("injected restore");
        });
        failed(owner.clear(), "recipe.registry.restore");
        assertEquals(1, recipes.size());
        assertSame(input, recipes.keySet().iterator().next());
        assertEquals(.5f, experience.get(xpKey));
        assertEquals(1, map.getRecipeList().size());
        owner.publicationProbe(stage -> {});
        success(owner.clear());
        assertTrue(recipes.isEmpty());
        assertTrue(experience.isEmpty());
    }

    @Test
    void conflictInSecondDeclarationReportsThatSourceAndPublishesNeither() {
        var first = map(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD), .5f));
        var second = map(effect(new ItemStack(Items.CARROT), new ItemStack(Items.COAL), .5f));
        var secondSource = new SourceOrigin("groovy", "OtherRecipes.groovy", 83, 5, "other", "recipes");
        var secondDefinition = new RecipeDefinition(secondSource, second.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.CARROT))),
                List.of(ItemValue.of(new ItemStack(Items.BREAD))), 80, 8, false);
        ItemStack foreign = new ItemStack(Items.CARROT);
        recipes.put(foreign, new ItemStack(Items.DIAMOND));
        var before = new FurnaceRegistrySnapshot(recipes, experience);
        var result = owner.replace("baking", List.of(definition(first), secondDefinition));
        assertTrue(result.value().isEmpty());
        assertEquals(secondSource, result.diagnostics().getFirst().origin());
        assertEquals("recipe.furnace.conflict", result.diagnostics().getFirst().code());
        assertTrue(before.isCurrent());
        assertTrue(first.getRecipeList().isEmpty());
        assertTrue(second.getRecipeList().isEmpty());
    }

    @Test
    void staticGroovyCreatesTheSameImmutableFurnaceDescriptor() {
        Object authored = new GroovyShell().evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.recipes.definition.FurnaceRecipeDefinition
                import net.minecraft.init.Items
                import net.minecraft.item.ItemStack
                @CompileStatic
                class FurnaceAuthoring {
                    static FurnaceRecipeDefinition make() {
                        FurnaceRecipeDefinition.of(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD, 2), 0.5f)
                    }
                }
                FurnaceAuthoring.make()
                """);
        assertEquals(effect(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD, 2), .5f), authored);
    }

    private static RecipeMap<SimpleRecipeBuilder> map(FurnaceRecipeDefinition... effects) {
        var map = new RecipeMapBuilder<>("typed_furnace_" + IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        addExpansion(map, effects);
        return map;
    }

    private static void addExpansion(RecipeMap<SimpleRecipeBuilder> map, FurnaceRecipeDefinition... effects) {
        map.onRecipeBuild(new ResourceLocation("test", "furnace_" + IDS.incrementAndGet()),
                new RecipeExpansion<SimpleRecipeBuilder>() {

                    @Override
                    public void expand(SimpleRecipeBuilder builder, Consumer<RecipeBuilder<?>> derived) {}

                    @Override
                    public List<FurnaceRecipeDefinition> furnaceRecipes(SimpleRecipeBuilder builder) {
                        return List.of(effects);
                    }
                });
    }

    private static FurnaceRecipeDefinition effect(ItemStack input, ItemStack output, float experience) {
        return FurnaceRecipeDefinition.of(input, output, experience);
    }

    private static RecipeDefinition definition(RecipeMap<?> map) {
        return new RecipeDefinition(SOURCE, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.APPLE))),
                List.of(ItemValue.of(new ItemStack(Items.BREAD))), 80, 8, false);
    }

    private static void success(DefinitionResult<?> result) {
        assertTrue(result.value().isPresent(), result.diagnostics().toString());
    }

    private static void failed(DefinitionResult<?> result, String code) {
        assertTrue(result.value().isEmpty());
        assertEquals(code, result.diagnostics().getFirst().code());
        assertEquals(SOURCE, result.diagnostics().getFirst().origin());
    }
}

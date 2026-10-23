package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeCandidateSelectionTest {

    private static int nextId;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void rejectedCachedRecipeFallsBackWithoutConsumingRejectedInputs() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = add(map, Items.IRON_INGOT);
        Recipe allowed = add(map, Items.GOLD_INGOT);
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        logic.previousRecipe = blocked;
        logic.eligibility = recipe -> recipe == allowed;

        logic.trySearchNewRecipe();

        assertSame(allowed, logic.previousRecipe);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(7, machine.getImportItems().getStackInSlot(1).getCount());
        assertEquals(1, logic.finalChecks);
        assertEquals(1, logic.preparations);
        assertTrue(logic.isActive());
    }

    @Test
    void eligibleCachedRecipeKeepsPriorityWithoutAnotherLookup() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, Items.IRON_INGOT);
        Recipe cached = add(map, Items.GOLD_INGOT);
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        logic.previousRecipe = cached;

        logic.trySearchNewRecipe();

        assertSame(cached, logic.previousRecipe);
        assertEquals(0, logic.searches);
        assertEquals(1, logic.finalChecks);
        assertEquals(1, logic.preparations);
    }

    @Test
    void environmentOnlyFailureRecoversWithoutAnInventoryNotification() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe recipe = add(map, Items.IRON_INGOT);
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        logic.eligibility = candidate -> false;
        logic.update();
        assertFalse(logic.invalidInputsForRecipes);
        assertSame(recipe, logic.previousRecipe);
        assertEquals(0, logic.finalChecks);
        assertEquals(0, logic.preparations);
        assertFalse(logic.hasNotifiedInputs());
        logic.eligibility = candidate -> true;
        for (machine.tick = 1; machine.tick < 20; machine.tick++) logic.update();
        assertEquals(1, logic.searches);
        assertEquals(0, logic.finalChecks);
        logic.update();
        assertEquals(1, logic.finalChecks);
        assertEquals(7, machine.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void inputNotificationWakesTemporaryFailureBeforeItsDeadline() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, Items.IRON_INGOT);
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        logic.eligibility = candidate -> false;
        logic.update();
        machine.tick = 1;
        logic.eligibility = candidate -> true;
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9));
        logic.update();
        assertEquals(1, logic.finalChecks);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void genuinelyUnmatchedInputsRemainNotificationGated() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, Items.DIAMOND);
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        logic.update();
        assertTrue(logic.invalidInputsForRecipes);
        for (machine.tick = 1; machine.tick <= 60; machine.tick++) logic.update();
        assertEquals(1, logic.searches);
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        logic.update();
        assertEquals(2, logic.searches);
        assertEquals(1, logic.finalChecks);
    }

    @Test
    void finalRejectionDoesNotRunAnotherPotentiallyMutatingCheck() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        add(map, Items.IRON_INGOT);
        add(map, Items.GOLD_INGOT);
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        logic.finalAllowed = false;
        logic.update();
        assertEquals(1, logic.finalChecks);
        assertEquals(0, logic.preparations);
        assertEquals(8, machine.getImportItems().getStackInSlot(0).getCount());
        assertEquals(8, machine.getImportItems().getStackInSlot(1).getCount());
        assertFalse(logic.invalidInputsForRecipes);
    }

    @Test
    void legacyLogicLookupOverrideIsNotBypassedToSearchTheRegisteredMap() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = add(map, Items.IRON_INGOT);
        Recipe allowed = add(map, Items.GOLD_INGOT);
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(populatedMachine(), map) {

            @Override
            protected Recipe findRecipe(long voltage, IItemHandlerModifiable items, IMultipleTankHandler fluids) {
                searches++;
                return blocked;
            }
        };
        logic.eligibility = recipe -> recipe == allowed;
        logic.trySearchNewRecipe();
        assertEquals(1, logic.searches);
        assertSame(blocked, logic.previousRecipe);
        assertEquals(0, logic.finalChecks);
        assertFalse(logic.invalidInputsForRecipes);
    }

    @Test
    void realDimensionAndCleanroomPropertiesArePureCandidateConstraints() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe dimensionBlocked = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .dimension(123456));
        Recipe cleanroomBlocked = register(map, map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT))
                .cleanroom(CleanroomType.CLEANROOM));
        RecipeCandidateTestMachine machine = populatedMachine();
        RecipeCandidateTestLogic logic = new RecipeCandidateTestLogic(machine, map);
        assertFalse(logic.isRecipeEligible(dimensionBlocked));
        assertFalse(logic.isRecipeEligible(cleanroomBlocked));
        logic.trySearchNewRecipe();
        assertFalse(logic.invalidInputsForRecipes);
        assertEquals(0, logic.finalChecks);
        assertEquals(0, logic.preparations);
    }

    @Test
    void replacingAndSuperDelegatingLegacyChecksKeepTheirOriginalFinalBehavior() {
        RecipeMap<SimpleRecipeBuilder> map = map();
        Recipe blocked = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).dimension(123456));
        AbstractRecipeLogic replacing = new AbstractRecipeLogic(populatedMachine(), map) {

            @Override
            public boolean checkRecipe(Recipe recipe) {
                return true;
            }
        };
        assertTrue(replacing.isRecipeEligible(blocked), "Unknown legacy checks are deferred until final selection");
        assertTrue(replacing.checkRecipe(blocked));
        AbstractRecipeLogic delegating = new AbstractRecipeLogic(populatedMachine(), map) {

            @Override
            public boolean checkRecipe(Recipe recipe) {
                return super.checkRecipe(recipe);
            }
        };
        assertTrue(delegating.isRecipeEligible(blocked));
        assertFalse(delegating.checkRecipe(blocked), "Delegating to the legacy base must still enforce dimension");
        AbstractRecipeLogic optedIn = new AbstractRecipeLogic(populatedMachine(), map) {

            @Override
            public boolean checkRecipe(Recipe recipe) {
                return super.checkRecipe(recipe);
            }

            @Override
            protected boolean supportsRecipeCandidateEligibility() {
                return true;
            }
        };
        assertFalse(optedIn.isRecipeEligible(blocked));
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("candidate_selection_" + nextId++, new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(2).build();
    }

    static Recipe add(RecipeMap<SimpleRecipeBuilder> map, Item input) {
        return register(map, map.recipeBuilder().inputs(new ItemStack(input)));
    }

    private static Recipe register(RecipeMap<SimpleRecipeBuilder> map, SimpleRecipeBuilder builder) {
        Recipe recipe = builder.outputs(new ItemStack(Items.APPLE)).EUt(1).duration(100).build().getResult();
        assertTrue(map.compileRecipe(recipe));
        return recipe;
    }

    static RecipeCandidateTestMachine populatedMachine() {
        RecipeCandidateTestMachine machine = new RecipeCandidateTestMachine();
        machine.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        machine.getImportItems().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 8));
        return machine;
    }
}

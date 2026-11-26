package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.ConfigHolder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeEnvironmentChecksTest {

    private static RecipeMap<SimpleRecipeBuilder> map;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        map = RecipeFailureTest.map();
    }

    private static SimpleRecipeBuilder builder() {
        return map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.APPLE))
                .EUt(1).duration(10);
    }

    private static Recipe cleanroomRecipe() {
        return builder().cleanroom(CleanroomType.CLEANROOM).build().getResult();
    }

    /** A machine outside any world that cannot receive a cleanroom. */
    private static MetaTileEntity plain() {
        return new MetaTileEntity(GTUtility.gregtechId("environment_check_plain")) {

            @Override
            public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
                return this;
            }
        };
    }

    @Test
    void aRecipeWithoutRequirementsAsksNothingOfTheMachine() {
        Recipe recipe = builder().build().getResult();
        MetaTileEntity machine = plain();
        assertNull(machine.getWorld(), "Reading the world would fail, so a pass shows it was not read");
        assertEquals(RecipeFailureReason.NONE, RecipeEnvironmentChecks.checkCleanroom(recipe, machine));
        assertEquals(RecipeFailureReason.NONE, RecipeEnvironmentChecks.checkDimension(recipe, machine));
    }

    @Test
    void aMachineThatCannotReceiveACleanroomHasNone() {
        assertEquals(RecipeFailureReason.CLEANROOM_MISSING,
                RecipeEnvironmentChecks.checkCleanroom(cleanroomRecipe(), plain()));
    }

    @Test
    void aCleanroomMustBePresentCleanAndOfTheRequiredTypeInThatOrder() {
        Recipe recipe = cleanroomRecipe();
        RecipeFailureTestMachine machine = new RecipeFailureTestMachine();
        assertEquals(RecipeFailureReason.CLEANROOM_MISSING, RecipeEnvironmentChecks.checkCleanroom(recipe, machine));

        RecipeFailureCleanroom cleanroom = new RecipeFailureCleanroom();
        machine.setCleanroom(cleanroom);
        assertEquals(RecipeFailureReason.CLEANROOM_DIRTY, RecipeEnvironmentChecks.checkCleanroom(recipe, machine));
        assertEquals(0, cleanroom.typeChecks, "A dirty cleanroom is not asked for its type");

        cleanroom.clean = true;
        assertEquals(RecipeFailureReason.CLEANROOM_TYPE, RecipeEnvironmentChecks.checkCleanroom(recipe, machine));
        cleanroom.correctType = true;
        assertEquals(RecipeFailureReason.NONE, RecipeEnvironmentChecks.checkCleanroom(recipe, machine));
    }

    @Test
    void theCleanMultiblocksSettingExemptsMultiblocksOnly() {
        Recipe recipe = cleanroomRecipe();
        RecipeFailureTestController controller = new RecipeFailureTestController(map);
        RecipeFailureTestMachine machine = new RecipeFailureTestMachine();
        boolean previous = ConfigHolder.machines.cleanMultiblocks;
        try {
            ConfigHolder.machines.cleanMultiblocks = true;
            assertEquals(RecipeFailureReason.NONE, RecipeEnvironmentChecks.checkCleanroom(recipe, controller));
            assertEquals(RecipeFailureReason.CLEANROOM_MISSING,
                    RecipeEnvironmentChecks.checkCleanroom(recipe, machine));
            ConfigHolder.machines.cleanMultiblocks = false;
            assertEquals(RecipeFailureReason.CLEANROOM_MISSING,
                    RecipeEnvironmentChecks.checkCleanroom(recipe, controller));
        } finally {
            ConfigHolder.machines.cleanMultiblocks = previous;
        }
    }

    @Test
    void aDimensionRequirementIsCheckedAgainstTheMachinesWorld() {
        RecipeFailureTestMachine machine = new RecipeFailureTestMachine();
        int dimension = DummyWorld.INSTANCE.provider.getDimension();
        Recipe here = builder().dimension(dimension).build().getResult();
        Recipe elsewhere = builder().dimension(dimension - 1).build().getResult();
        assertEquals(RecipeFailureReason.NONE, RecipeEnvironmentChecks.checkDimension(here, machine));
        assertEquals(RecipeFailureReason.DIMENSION, RecipeEnvironmentChecks.checkDimension(elsewhere, machine));
    }
}

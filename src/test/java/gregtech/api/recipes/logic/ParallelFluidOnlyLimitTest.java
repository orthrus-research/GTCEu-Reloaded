package gregtech.api.recipes.logic;

import gregtech.Bootstrap;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelFluidOnlyLimitTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void fluidOnlyRecipeIsLimitedToTheRequestedParallel() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("test_fluid_only_parallel",
                new SimpleRecipeBuilder().EUt(30))
                .fluidInputs(1)
                .fluidOutputs(1)
                .build();
        Recipe recipe = map.recipeBuilder()
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 1))
                .fluidOutputs(new FluidStack(FluidRegistry.LAVA, 1))
                .EUt(0).duration(1)
                .build().getResult();

        FluidTankList outputs = new FluidTankList(false, new FluidTank(Integer.MAX_VALUE));
        assertEquals(4, ParallelLogic.limitByOutputMerging(recipe, new ItemStackHandler(1), outputs, 4, false,
                false));
    }
}

package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeIdentityTest {

    private static RecipeMap<SimpleRecipeBuilder> map;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        map = new RecipeMapBuilder<>("recipe_identity_test", new SimpleRecipeBuilder())
                .itemInputs(2).fluidInputs(2).fluidOutputs(3).build();
    }

    @Test
    void equalRecipesHashAlikeWhateverTheInputOrder() {
        Recipe first = map.recipeBuilder().inputs(new ItemStack(Items.APPLE), new ItemStack(Items.STICK))
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 100), new FluidStack(FluidRegistry.LAVA, 10))
                .EUt(1).duration(1).build().getResult();
        Recipe second = map.recipeBuilder().inputs(new ItemStack(Items.STICK), new ItemStack(Items.APPLE))
                .fluidInputs(new FluidStack(FluidRegistry.LAVA, 10), new FluidStack(FluidRegistry.WATER, 100))
                .EUt(1).duration(1).build().getResult();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void chancedFluidsWithDifferentTagsStaySeparate() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Kind", "tagged");
        FluidStack tagged = new FluidStack(FluidRegistry.WATER, 10, tag);
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Items.APPLE))
                .chancedFluidOutput(new FluidStack(FluidRegistry.WATER, 10), 10000, 0)
                .chancedFluidOutput(tagged, 10000, 0)
                .chancedFluidOutput(new FluidStack(FluidRegistry.WATER, 5), 10000, 0)
                .EUt(1).duration(1).build().getResult();

        List<FluidStack> outputs = recipe.getResultFluidOutputs(1, 1, map);

        assertEquals(2, outputs.size());
        assertEquals(15, outputs.get(0).amount);
        assertEquals(null, outputs.get(0).tag);
        assertEquals(10, outputs.get(1).amount);
        assertEquals(tag, outputs.get(1).tag);
    }
}

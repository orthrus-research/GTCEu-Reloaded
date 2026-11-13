package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RecipeMapManyInputsTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void findsRecipeWhoseInputsAreMoreThan64SlotsApart() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("test_many_inputs",
                new SimpleRecipeBuilder().EUt(30))
                .itemInputs(2)
                .itemOutputs(1)
                .build();
        map.recipeBuilder()
                .inputs(new ItemStack(Blocks.DIRT))
                .inputs(new ItemStack(Blocks.STONE))
                .outputs(new ItemStack(Items.DIAMOND))
                .EUt(1).duration(1)
                .buildAndRegister();

        // dirt first, stone at index 64, 63 unrelated items between them
        List<ItemStack> inputs = new ArrayList<>();
        inputs.add(new ItemStack(Blocks.DIRT));
        for (Item item : Item.REGISTRY) {
            if (inputs.size() == 64) break;
            if (item == Item.getItemFromBlock(Blocks.DIRT) || item == Item.getItemFromBlock(Blocks.STONE) ||
                    item == Items.AIR) {
                continue;
            }
            inputs.add(new ItemStack(item));
        }
        inputs.add(new ItemStack(Blocks.STONE));

        assertNotNull(map.findRecipe(30, inputs, Collections.emptyList()));
    }
}

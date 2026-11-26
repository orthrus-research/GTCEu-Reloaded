package com.example.gtaddon;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;

/** A recipe builder subclass, a new recipe map and recipes in GT's own maps. */
public final class FixtureRecipes {

    public static final RecipeMap<PressRecipeBuilder> PRESS_RECIPES = new RecipeMapBuilder<>("fixture_press",
            new PressRecipeBuilder())
            .itemInputs(2)
            .itemOutputs(1)
            .fluidInputs(1)
            .build();

    private FixtureRecipes() {}

    static void register() {
        RecipeMaps.MACERATOR_RECIPES.recipeBuilder()
                .inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(OreDictUnifier.get(OrePrefix.dust, Materials.Iron))
                .duration(100).EUt(30)
                .buildAndRegister();
        PRESS_RECIPES.recipeBuilder()
                .pressure(2)
                .input(OrePrefix.ingot, FixtureMaterials.fixtureAlloy)
                .inputs(FixtureItems.widgets(2))
                .notConsumable(new ItemStack(Items.STICK))
                .fluidInputs(Materials.Water.getFluid(100))
                .outputs(FixtureMaterials.plate())
                .duration(20).EUt(8)
                .buildAndRegister();
    }

    static Recipe find() {
        return RecipeMaps.MACERATOR_RECIPES.findRecipe(30, List.of(new ItemStack(Items.IRON_INGOT)), List.of());
    }

    /** A builder with one setting of its own. */
    public static final class PressRecipeBuilder extends RecipeBuilder<PressRecipeBuilder> {

        private int pressure;

        public PressRecipeBuilder() {}

        public PressRecipeBuilder(RecipeBuilder<PressRecipeBuilder> other) {
            super(other);
        }

        public PressRecipeBuilder pressure(int pressure) {
            this.pressure = pressure;
            return this;
        }

        @Override
        public PressRecipeBuilder copy() {
            PressRecipeBuilder copy = new PressRecipeBuilder(this);
            copy.pressure = pressure;
            return copy;
        }
    }
}
